# TOOL001 — Package Tool

Status: IN_PROGRESS

Nature: non-normative project implementation record

Architecture owners:

- `docs/design/TOOLCHAIN_TOOL_ARCHITECTURE.md`
- `docs/design/PACKAGE_TOOL_ARCHITECTURE.md`
- package format/identity/resolution/lock/manifest design records under
  `docs/design/`

Canonical summary:

- `docs/project/IMPLEMENTATION_STATUS.md`

## Purpose

TOOL001 tracks the official toolchain-bundled Package Tool as one developer-tool
entity independent from the public CLI spelling used to reach it.

The public driver may expose `protos package ...` or later map flatter commands
to the same bundled tool. That UX choice does not move package policy into the
`CLIxxx` family. Package resolution, manifest/lock interpretation, graph policy,
metadata mutation policy, and later registry policy remain Package Tool work.

## Retrospective migration rule

Package Tool implementation began before the `TOOLxxx` family was introduced.
This record therefore **indexes history; it does not rewrite history**.

Do not rename historical commits, changelog text, blockers, or design references
merely to make them use TOOL001 spelling. Existing labels remain valid evidence,
including:

- bundled package-tool bootstrap slice;
- Package-tool Filesystem Slice 2A;
- Package-tool Filesystem Slice 2B / B006;
- package-tool manifest Slice 3 and its published sub-slices.

From this record onward, TOOL001 is the canonical parent lifecycle. When a new
implementation slice is formalized, use a TOOL001 slice identifier and record
its relation to any legacy Slice 3 terminology that remains useful for continuity.

## Published implementation mapped into TOOL001

| TOOL001 slice | Historical label | Status | Evidence | Meaning |
|---|---|---|---|---|
| TOOL001-A | bundled package-tool bootstrap slice | CLOSED | `9c336932c502163c97ab02d3e6ba0c6ee6d10c26` | `protos package` selects an exact bundled Protos entry without resolving the tool through the user's project graph. |
| TOOL001-B1 | Package-tool Filesystem Slice 2A | CLOSED | `f5738f8d1063cdf6e2d969b792d786177bfa8a36` | Package Tool receives explicit read-only authority confined to project metadata. |
| TOOL001-B2 | Package-tool Filesystem Slice 2B / B006 | CLOSED | `f128293fbe769cc8806879b0784262b56a08a4ba` | Package Tool receives narrowly scoped staging-write/namespace-mutation authority and publishes metadata through ordinary standard File/Filesystem operations. |
| TOOL001-C1 | package-tool manifest Slice 3A | CLOSED | `8150d8219664b50ec66748639a47a1629638a8ed` | Internal `self:TomlSyntax` parser foundation is written in Protos and deliberately does not acquire package-schema meaning. |
| TOOL001-C2 | package-tool manifest Slice 3B1 | CLOSED | `dc82976cd95ad4f08d446fbb7aedb43adf818612` | TOML 1.0 String surface required by manifest schema v1 is completed; schema/CLI behavior remains above the syntax engine. |
| TOOL001-C3 | package-tool manifest Slice 3B2-A | CLOSED | `SAME_COMMIT` | `self:TomlDocument.statements(text)` provides document-level lexical statement segmentation without table/schema meaning. |
| TOOL001-C4 | package-tool manifest Slice 3B2-B | CLOSED | `SAME_COMMIT` | Canonical TOML document/table assembly is complete across C4A ordinary tables and C4B arrays-of-tables; schema/package meaning remains above this parser boundary. |
| TOOL001-C4A | package-tool manifest Slice 3B2-B1 | CLOSED | `SAME_COMMIT` | `self:TomlDocument.table(text)` assembles ordinary TOML headers, dotted keys and inline tables into the canonical nested node/Map model with TOML redefinition invariants. |
| TOOL001-C4B | package-tool manifest Slice 3B2-B2 | CLOSED | `SAME_COMMIT` | TOML 1.0 arrays-of-tables append in source order, nested headers resolve through the latest array element, and table/array/static-array conflicts fail closed. |
| TOOL001-C5 | package-tool manifest Slice 3C | CLOSED | `SAME_COMMIT` | Schema-v1 implementation is complete across C5A parser-scale robustness, C5B mandatory root/package base, C5C compatibility/exports/workspace and C5D dependency declarations plus complete ordinary ManifestV1 construction. |
| TOOL001-C5A | package-tool manifest Slice 3C prerequisite | CLOSED | `SAME_COMMIT` | Preserve C3/C4 TOML semantics while removing one-call-per-octet and one-call-per-statement linear stack growth; add manifest-scale Protos regression. |
| TOOL001-C5B | package-tool manifest Slice 3C1 | CLOSED | `SAME_COMMIT` | `self:ManifestSchemaV1.parseBase/baseFromTable` validates the complete root-name allowlist plus exact generation 1 and required package id/version/optional locator, returning ordinary-Protos package data while leaving known optional sections to later slices. |
| TOOL001-C5C | package-tool manifest Slice 3C2 | CLOSED | `SAME_COMMIT` | `self:ManifestSchemaV1.sectionsFromTable/parseSections` validates and models optional compatibility, exports and workspace data over C5B, with fail-closed owned fields, exact Strings, empty exports/members support and duplicate-free workspace members; dependencies remain intentionally deferred. |
| TOOL001-C5D | package-tool manifest Slice 3C3 | CLOSED | `SAME_COMMIT` | `self:ManifestSchemaV1.fromTable/parse` completes the ordinary ManifestV1 model with user-keyed dependencies and exact registry/Git/path structural forms; mixed/incomplete/unknown declarations fail closed and C5 cross-schema conformance is published. |
| TOOL001-C6 | package-tool manifest Slice 3D | CLOSED | `SAME_COMMIT` | `self:ManifestCommand` reads exactly `protos.toml` through the provisioned confined Filesystem, consumes complete UTF-8 text across progress chunks, invokes the closed ManifestV1 parser and owns read/schema diagnostics; the host driver mechanically selects exact bundled `ManifestMain` only for `protos package manifest`, leaving package policy in Protos and the historical bare `Main` entry unchanged. |
| TOOL001-C7 | package-tool manifest Slice 3 closure | CLOSED | `SAME_COMMIT` | Final cross-slice validation plus architecture/status reconciliation closes the bounded legacy manifest Slice 3 surface without new executable behavior or implementation-version increment. |
| TOOL001-C | historical manifest Slice 3 parent | CLOSED | `SAME_COMMIT` | C1-C7 are published: canonical TOML, schema-v1 structural model, confined project-manifest read/diagnostics and final cross-slice reconciliation are complete. Later version/lock/resolution/workspace/store/registry work is outside this bounded Slice 3 parent. |
| TOOL001-D | release-version / dependency-constraint value policy | CLOSED | `SAME_COMMIT` | D1 ReleaseVersion plus D2A-D2D dependency constraint v1 are published: exact/caret/bounded interval syntax, precedence satisfaction and explicit same-core prerelease admission are complete pure bundled-Protos value policy. Resolver/lock work remains separate. |
| TOOL001-D1 | strict ReleaseVersion value + precedence | CLOSED | `SAME_COMMIT` | `self:ReleaseVersion.parse/compare/compareText` implements the selected SemVer-derived package release value: exact three-component core, optional validated prerelease, no build metadata, arbitrary-size Integer components and SemVer precedence. |
| TOOL001-D2 | dependency constraint language v1 | CLOSED | `SAME_COMMIT` | D2A exact, D2B caret, D2C bounded intervals and D2D same-core prerelease admission/cross-form conformance are complete. |
| TOOL001-D2A | exact dependency constraints | CLOSED | `SAME_COMMIT` | `self:DependencyConstraint.parse/satisfies/satisfiesText` accepts only one bare full ReleaseVersion and matches it by exact D1 precedence equality; exact prereleases are supported only when named literally. |
| TOOL001-D2B | caret dependency constraints | CLOSED | `SAME_COMMIT` | Caret bound construction plus stable-candidate satisfaction over D1 ReleaseVersion; zero-major rules are explicit and prerelease satisfaction remains fail-closed until D2D. |
| TOOL001-D2C | explicit bounded intervals | CLOSED | `SAME_COMMIT` | Exactly two whitespace-joined primitive comparisons with one lower and one upper bound; stable-candidate satisfaction honors inclusive/exclusive endpoints and prerelease satisfaction remains fail-closed until D2D. |
| TOOL001-D2D | prerelease admission + D2 closure | CLOSED | `SAME_COMMIT` | Stable constraints reject prerelease candidates by default; prereleases are admitted only when a constraint explicitly names a prerelease for the same core tuple, then ordinary exact/range precedence applies. Final cross-form conformance closes D2 and parent D. |
| TOOL001-E | local/offline version selection policy | CLOSED | `SAME_COMMIT` | E1 fresh highest-satisfying selection plus E2 retained exact-version preference are published as pure local version policy over already-known candidates. Discovery, full eligibility, graph resolution and physical lock work remain separate. |
| TOOL001-E1 | fresh highest-satisfying ReleaseVersion selection | CLOSED | `SAME_COMMIT` | `self:FreshVersionSelection.select/selectText` filters already-known ReleaseVersion candidates through closed D2 constraint semantics and selects the highest satisfying candidate by D1 precedence; no-match fails closed. |
| TOOL001-E2 | retained exact-version preference | CLOSED | `SAME_COMMIT` | `self:RetainedVersionSelection.select/selectText` preserves an available exact retained ReleaseVersion while it still satisfies D2; otherwise it delegates to E1 fresh selection. No physical lockfile or package-identity policy is implied. |
| TOOL001-F | canonical physical lockfile v1 | IN_PROGRESS | TOOL001-F1 CLOSED; F2D CLOSED; F2E0 `SAME_COMMIT` | Canonical lockfile + workspace execution are published; F2E external immutable-package execution is the active continuation. |
| TOOL001-F1A | canonical lock header grammar | CLOSED | `SAME_COMMIT` | Exact three-line v1 header grammar and canonical lexical rules are frozen without implementing a parser/writer or choosing body node/edge syntax. |
| TOOL001-F1B | canonical lock body node/edge grammar | CLOSED | `SAME_COMMIT` | F1B1 scalar/reference, F1B2 root/workspace and F1B3 external-node/dependency/final ordering decisions freeze the complete canonical body grammar for lock-format 1. |
| TOOL001-F1B1 | canonical scalar strings + typed node references | CLOSED | `SAME_COMMIT` | Body variable values use one deterministic quoted UTF-8 scalar encoding; node references are source-kind-tagged tuples over quoted identity components, avoiding delimiter-composed PackageId keys while PackageId textual encoding remains open. |
| TOOL001-F1B2 | root/workspace representation | CLOSED | `SAME_COMMIT` | Exactly one root workspace-ref identifies the root manifest package; additional workspace member declarations map their exact manifest string to a workspace-ref in canonical order, without introducing virtual-workspace identity or path semantics. |
| TOOL001-F1B3 | external node blocks + dependency edges + F1B closure | CLOSED | `SAME_COMMIT` | Flat registry/git external records, mandatory ContentIdentity, registry locator+authority, Git fetch provenance, exact alias->target edges, total body ordering/separation and omission of ArtifactDigest close F1B. |
| TOOL001-F1C | canonical lock parser/writer + round-trip conformance | CLOSED | `SAME_COMMIT` | F1C1 lexical primitives, F1C2 structural body model and F1C3 canonical total writer/rejection/round-trip conformance complete the pure in-memory lock-format-1 parser/writer boundary. |
| TOOL001-F2 | physical lock integration | IN_PROGRESS | TOOL001-F2A/F2B/F2C/F2D CLOSED; F2E0 `SAME_COMMIT` | Workspace-only normal execution is CLOSED. F2E external immutable-package execution is allocated; E1 canonical ContentIdentity tree contract is READY. |
| TOOL001-F2A | confined `protos.lock` read/publish substrate | CLOSED | `SAME_COMMIT` | `self:LockFile.load` reads canonical `protos.lock`; `publish` validates/canonicalizes before `.protos.lock.stage -> protos.lock` MetadataPublication. No resolver/stale/CLI behavior. |
| TOOL001-F2B | semantic resolution-input + stale detection | CLOSED | `SAME_COMMIT` | F2B1/F2B2 semantic model plus F2B3 canonical byte serialization, SHA-256 digest/header identity and read-only stale comparison complete F2B. |
| TOOL001-F2C | physical resolution-root assembly | CLOSED | `SAME_COMMIT` | Pure Protos reads root + explicit member protos.toml through supplied confined tree Filesystem and returns the F2B semantic root with normalized registry/Git/path dependency projections. |
| TOOL001-F2D | workspace-only normal-execution preflight + PackageExecutionPlan | CLOSED | `SAME_COMMIT` | Bounded workspace-only normal execution is complete: canonical non-stale workspace locks preflight read-only and run through exact package-backed modules. External registry/Git materialization remains future F2 work. |
| TOOL001-F2D1 | PackageExecutionPlan ABI + runtime-name/preflight contract | CLOSED | `SAME_COMMIT` | Freeze workspace-only lock reconciliation, alias/export/module portable-name policy, inert plan shape, authority separation, defensive detach boundary and explicit external-node rejection. |
| TOOL001-F2D2 | pure workspace execution-state + plan construction | CLOSED | `SAME_COMMIT` | Single-pass full ManifestV1 + ResolutionRootV1 state, runtime-name validation, exact lock/body reconciliation and inert plan projection implemented in Protos. |
| TOOL001-F2D3 | mechanical host resolver handoff + command-scoped workspace preflight | CLOSED | `SAME_COMMIT` | Immutable plan detach, exact workspace resolver and command-scoped workspace run handoff are fully published. |
| TOOL001-F2D3A | immutable host DTO + defensive plan detach | CLOSED | `SAME_COMMIT` | Validate exact generation-1 workspace plan shape and recursively detach ordinary Protos data into immutable host records/Lists/Maps; no resolver or CLI integration. |
| TOOL001-F2D3B | exact workspace package-backed module resolver | CLOSED | `SAME_COMMIT` | B1 identity/source plus B2 self:/dep:/std: routing complete the workspace resolver. |
| TOOL001-F2D3B1 | package identity + source mechanism | CLOSED | `SAME_COMMIT` | Canonical workspace ModuleKey identity plus exact package-directory and confined logical-source mapping complete B1. |
| TOOL001-F2D3B1A | canonical workspace ModuleKey codec | CLOSED | `SAME_COMMIT` | Host-only key identity = exact workspace PackageId + portable internal logical module, serialized in a workspace-specific canonical base64url domain with no paths/aliases/exports. |
| TOOL001-F2D3B1B | physical source mechanism | CLOSED | `SAME_COMMIT` | Root/index, exact member-directory binding and exact regular confined `.protos` lookup complete B1B. |
| TOOL001-F2D3B1B1 | selected project-root anchor + detached package index | CLOSED | `SAME_COMMIT` | Anchor real selected project root and build exact immutable host indexes by PackageId/location; no member path traversal or source loading. |
| TOOL001-F2D3B1B2 | exact member-location directory binding | CLOSED | `SAME_COMMIT` | Exact child lookup, confined canonical traversal and immutable package-directory binding closed across B1B2A/B/C. |
| TOOL001-F2D3B1B2A | exact direct-child directory lookup | CLOSED | `SAME_COMMIT` | Enumerate one already-selected parent and require one exact stored child spelling that denotes a directory; no multi-segment traversal or confinement decision. |
| TOOL001-F2D3B1B2B | confined canonical member-location traversal | CLOSED | `SAME_COMMIT` | Split canonical non-root locations on `/`, traverse each exact child, resolve each selected directory, permit only in-root symlinks and return the final real directory. |
| TOOL001-F2D3B1B2C | immutable package -> physical-directory binding | CLOSED | `SAME_COMMIT` | Apply B1B2B to every detached B1B1 package and index immutable PackageNode + real-directory bindings by exact PackageId/location. |
| TOOL001-F2D3B1B3 | logical module -> exact regular `.protos` source | CLOSED | `SAME_COMMIT` | Validate portable logical names and map them by exact case-unambiguous spelling to one regular real `.protos` source confined to the package root. |
| TOOL001-F2D3B2 | resolver routing | CLOSED | `SAME_COMMIT` | self:/dep:/std: routing complete; unsupported spellings fail closed. |
| TOOL001-F2D3B2A | self: routing | CLOSED | `SAME_COMMIT` | Root entry identity is explicit; self: resolves relative to the importing workspace PackageId, bypasses exports and uses exact B1B3 source. |
| TOOL001-F2D3B2B | dep: edge/export routing | CLOSED | `SAME_COMMIT` | Resolve only the importing package's exact detached alias edge, then the target's exact public export -> internal logical module; aliases/exports never enter ModuleKey identity. |
| TOOL001-F2D3B2C | std: delegation + resolver closure | CLOSED | `SAME_COMMIT` | Exact std: resolution/source loading delegates to the explicitly selected Standard Library resolver. |
| TOOL001-F2D3C | command-scoped workspace preflight + authority separation | CLOSED | `SAME_COMMIT` | Read-only tool preflight, detached-plan application execution, authority-isolation proof and public workspace-run wiring are complete. |
| TOOL001-F2D3C1 | read-only Package Tool preflight -> detached plan | CLOSED | `SAME_COMMIT` | Fresh tool Process receives only confined read-only projectTreeFilesystem; ExecutionPlan.build result is detached before tool Process termination. |
| TOOL001-F2D3C2 | detached plan -> separately-authorized application Process | CLOSED | `SAME_COMMIT` | C2A canonical initial-module execution, C2B fresh application Process wiring and C2C C1->C2 authority-isolation integration are all closed. |
| TOOL001-F2D3C2A | TOOL001 F2D3 application execution: canonical initial-module execution primitive | CLOSED | `SAME_COMMIT` | Cache the supplied RootActor bootstrap module context under one canonical ModuleKey before task execution; mark READY on completion and remove on failure/cancellation. No package/workspace/Process lifecycle policy. |
| TOOL001-F2D3C2B | TOOL001 F2D3 application execution: detached plan -> fresh application Process wiring | CLOSED | `SAME_COMMIT` | Reconstruct the exact workspace resolver from detached DTO data, select the explicit root-package entry, bootstrap one fresh application Process from copied args/environment plus explicitly supplied streams/Encoding bindings, execute through C2A, and terminate the Process. |
| TOOL001-F2D3C2C | TOOL001 F2D3 application execution: C1->C2 authority-isolation integration + closure | CLOSED | `SAME_COMMIT` | Real C1->C2B integration proves the detached DTO is the only Package Tool result crossing the boundary, tool/application Processes are distinct and terminated, and projectTreeFilesystem is unavailable to application source. |
| TOOL001-F2D3C3 | public workspace-run wiring + F2D3/F2D closure | CLOSED | `SAME_COMMIT` | C3A CLI-neutral driver plus C3B explicit public `protos run <entry> [args...]` wiring complete the workspace-run boundary. |
| TOOL001-F2D3C3A | TOOL001 F2D3 public-run integration: CLI-neutral workspace-run driver | CLOSED | `8769e106dceeb7b7d6bf2c888a24a74f18b08e6e` | CLI-neutral C1->C2 workspace-run driver with explicit project root and logical entry. |
| TOOL001-F2D3C3B | TOOL001 F2D3 public-run integration: public `protos run` wiring + final F2D3/F2D closure | CLOSED | `SAME_COMMIT` | Public CLI selects the current directory as project root and requires an explicit root-package logical entry; application args start after the entry, diagnostics translate the closed driver outcome, and no default application Filesystem is granted. |
| TOOL001-F2E | external immutable-package execution continuation | IN_PROGRESS | `TOOL001-F2E0 SAME_COMMIT` | F2D workspace-only execution is CLOSED. E0 prerequisite audit/decomposition is CLOSED; E1 canonical ContentIdentity tree contract is READY. Fetch/network remains a separate authority path. |
| TOOL001-F2E0 | external materialization prerequisite audit + decomposition | CLOSED | `SAME_COMMIT` | Fresh post-F2D audit preserves fail-closed external execution and allocates E1-E5 without an executable shortcut. |
| TOOL001-F2E1 | `protos-package-tree-v1` ContentIdentity canonical tree contract | IN_PROGRESS | F2E1A CLOSED; F2E1B `SAME_COMMIT` | E1A logical-tree/path domain and E1B exact canonical byte stream + sha256 contract are CLOSED; E1C independent conformance vectors/closure is READY. |
| TOOL001-F2E1A | ContentIdentity logical-tree domain + portable path/entry-kind contract | CLOSED | `SAME_COMMIT` | Define ContentIdentity over the already-materialized payload, include every valid regular-file path+bytes, require root `protos.toml`, make directories structural/empty directories non-semantic, reject symlink/special entries, ignore host metadata and freeze conservative portable ASCII artifact paths/collision rules. |
| TOOL001-F2E1B | canonical byte stream + method/hash contract | CLOSED | `SAME_COMMIT` | Serialize the E1A path->bytes map as one method-domain-separated binary stream: exact ASCII path-byte order, FILE/END tags, minimal arbitrary-precision base-128 lengths, direct content bytes, and sha256 as the initial mandatory digest algorithm. |
| TOOL001-F2E1C | independent conformance vectors + F2E1 closure | READY | — | E1A/E1B semantics are frozen; publish independent positive/negative vectors with exact expected digests and close parent E1. |
| TOOL001-F2E2 | verified read-only package-store binding | BLOCKED_BY_DEPENDENCIES | — | Depends on F2E1. Re-audit the general Filesystem capability after E1 fixes required observations; no package-specific Java tree walker. |
| TOOL001-F2E3 | external-node execution-plan construction | BLOCKED_BY_DEPENDENCIES | — | Depends on F2E2. Extend Protos-owned preflight only from exact store-confined ContentIdentity-verified registry/Git nodes. |
| TOOL001-F2E4 | external canonical ModuleKey + source resolver | BLOCKED_BY_DEPENDENCIES | — | Depends on F2E3. Mechanical host identity uses exact immutable package instance + internal logical module, never locator/cache path/alias. |
| TOOL001-F2E5 | public run integration + F2 external-execution closure | BLOCKED_BY_DEPENDENCIES | — | Depends on F2E4. Extend normal run without version solving, implicit fetch, package-store authority leakage or lock mutation. |


| TOOL001-F2B1 | per-manifest semantic resolution-input projection design | CLOSED | `SAME_COMMIT` | Freeze resolver-affecting manifest inclusion/exclusion, D2 semantic constraint normalization, deterministic scalar/order owners and fail-closed unresolved-owner rule. No digest implementation. |
| TOOL001-F2B2 | resolution-root/workspace semantic assembly design | CLOSED | `SAME_COMMIT` | Root-only workspace expansion, canonical member paths, confined in-root path-dependency targeting, exact LanguageCompatibilityId and deterministic root/member assembly frozen. |
| TOOL001-F2B3 | resolution-input digest + stale comparison | CLOSED | `SAME_COMMIT` | Canonical ResolutionRootV1 bytes + std:crypto/SHA256 + lowercase hex + header match and LockFile.isStale, with no resolver/update side effects. |


| TOOL001-F1C1 | lock lexical/header/qstring/node-ref primitives | CLOSED | `SAME_COMMIT` | `self:LockSyntax` owns strict canonical line tokens, qstring parse/render, lock-format-1 header parse/render and typed workspace/registry/git node refs; registry versions reuse D1 ReleaseVersion. |
| TOOL001-F1C2 | body record/model + structural validation | CLOSED | `SAME_COMMIT` | `self:LockDocument` parses the complete in-memory v1 document/body model and rejects missing/duplicate nodes, duplicate declaring-alias edges and dangling references; canonical class/order rejection remains F1C3. |
| TOOL001-F1C3 | total writer + canonical rejection + F1C closure | CLOSED | `SAME_COMMIT` | Canonical F1B total ordering/writer plus public parse-write byte equality reject structurally valid non-canonical documents and close F1C. |





B006's normative prerequisite path through I021 remains historical evidence; it
is not reopened by this tracking migration.

## Current continuation boundary

The historical manifest Slice 3, pure version/constraint parent `TOOL001-D`, and
local/offline version-selection parent `TOOL001-E` are CLOSED.

`TOOL001-F` is the current bounded continuation for the canonical physical
`protos.lock` v1 format. `TOOL001-F1A` and `TOOL001-F1B` are CLOSED: the complete
canonical header/body grammar is frozen through F1B1/F1B2/F1B3.

`TOOL001-F1C` is CLOSED: F1C1 lexical primitives, F1C2 structural body
model and F1C3 canonical writer/rejection/round-trip conformance complete the
pure in-memory lock-format-1 parser/writer boundary.

`TOOL001-F2 — physical lock integration` is IN_PROGRESS through closed F2A.
F2A uses the already-confined Package Tool Filesystem to load canonical
`protos.lock` and atomically publish already-resolved canonical models through
the existing B2 metadata transaction.

`TOOL001-F2B — semantic resolution-input + stale detection` is IN_PROGRESS
through closed F2B1. F2B1 freezes the per-manifest resolver-affecting
inclusion/exclusion matrix, semantic constraint normalization ownership and the
fail-closed rule for unresolved semantic owners.

`TOOL001-F2B2` is CLOSED. One active root workspace, canonical root-relative
member paths, in-root path-dependency targeting, exact language-compatibility
identity and deterministic root/member assembly now complete the semantic
resolution-input model.

`TOOL001-F2B` is CLOSED. F2B1/F2B2 define the semantic input and F2B3 now
publishes canonical `protos-resolution-input-v1` bytes, SHA-256/lowercase-hex
header identity and read-only canonical-lock stale comparison.

`TOOL001-F2C` is CLOSED. `self:ResolutionRoot` constructs the semantic F2B
input from physical root/member manifests through explicit confined read-only
project-tree authority.

`TOOL001-F2D` is IN_PROGRESS through CLOSED F2D2. Pure Protos now owns the
complete workspace preflight policy up to an inert PackageExecutionPlanV1:
single-pass physical ManifestV1/ResolutionRootV1 state, canonical non-stale lock
validation, root/member/path-edge reconciliation, runtime-name/export validation
and plan projection.

`TOOL001-F2D3` is READY. It is mechanically limited to defensive plan detach,
exact package-backed module resolution and command-scoped preflight/application
authority separation. F2D continues to fail closed on registry/Git nodes until
external materialization + ContentIdentity verification is implemented.

A separate non-committing note in `docs/design/PACKAGE_TOOL_ARCHITECTURE.md`
records future reusable-library extraction opportunities for the schema-neutral
TOML front-end and the generic SemVer parse/precedence core. Those opportunities
do not create work items and do not block the current TOOL001-F lockfile path.

## Manifest Slice 3 final closure

`TOOL001-C` is CLOSED. Its bounded outcome is the published composition of:

```text
C1/C2     TOML lexical/value surface
C3/C4     canonical TOML document/table model
C5        schema-v1 structural validation and ordinary ManifestV1 construction
C6        confined protos.toml read, complete UTF-8 decode and diagnostics
C7        cross-slice validation plus architecture/status reconciliation
```

The closure deliberately does not claim package resolution, semantic version or
constraint validation, lockfile interpretation/generation, workspace path
policy, package-store/archive behavior, registry/network behavior, credentials,
fetch, update, or publication. Those concerns remain separate future Package
Tool work and require a fresh audit/slice definition before implementation.

The published host contribution remains mechanical bootstrap/authority:
selecting exact bundled entries and provisioning the already-defined confined
Filesystem capability. TOML/schema/read/diagnostic policy remains in bundled
Protos source. The historical bare package entry remains distinct from the
`ManifestMain` entry selected for `protos package manifest`.

## Family boundaries

- TOOL001 is **not** CLI implementation merely because the user invokes it via
  `protos`.
- TOOL001 is **not** Standard Library; bundled implementation under
  `protos/tools/package/` is not exposed through `std:` by virtue of shipping in
  the toolchain.
- TOOL001 is **not** PERF work. Performance investigations may measure Package
  Tool behavior without owning Package Tool policy.
- TOOL001 does not establish a third-party plugin model.

## Closure rule

TOOL001 closes only when the bounded Package Tool outcome selected by its current
architecture/project plan is implemented, validated, and published. Completion
of one legacy or TOOL001 sub-slice never closes the parent by implication.

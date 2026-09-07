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
| TOOL001-D | release-version / dependency-constraint value policy | IN_PROGRESS | TOOL001-D1 published | Pure bundled-Protos package value semantics after closed structural manifest parsing; D1 ReleaseVersion is closed, D2 dependency constraint v1 is READY, later resolver/lock work remains separate. |
| TOOL001-D1 | strict ReleaseVersion value + precedence | CLOSED | `SAME_COMMIT` | `self:ReleaseVersion.parse/compare/compareText` implements the selected SemVer-derived package release value: exact three-component core, optional validated prerelease, no build metadata, arbitrary-size Integer components and SemVer precedence. |
| TOOL001-D2 | dependency constraint language v1 | IN_PROGRESS | TOOL001-D2A/D2B published | D2A exact and D2B caret CLOSED; D2C bounded intervals READY; D2D prerelease/cross-form closure remains dependency-gated. |
| TOOL001-D2A | exact dependency constraints | CLOSED | `SAME_COMMIT` | `self:DependencyConstraint.parse/satisfies/satisfiesText` accepts only one bare full ReleaseVersion and matches it by exact D1 precedence equality; exact prereleases are supported only when named literally. |
| TOOL001-D2B | caret dependency constraints | CLOSED | `SAME_COMMIT` | Caret bound construction plus stable-candidate satisfaction over D1 ReleaseVersion; zero-major rules are explicit and prerelease satisfaction remains fail-closed until D2D. |
| TOOL001-D2C | explicit bounded intervals | READY | — | Add exactly two whitespace-separated lower/upper primitive comparisons using `>`, `>=`, `<`, `<=`; reject open-ended/unbounded forms; prerelease admission remains D2D. |
| TOOL001-D2D | prerelease admission + D2 closure | BLOCKED_BY_DEPENDENCIES | — | After D2C, compose exact/caret/interval forms with selected prerelease-admission rules, cross-form conformance and final D2 reconciliation. |


B006's normative prerequisite path through I021 remains historical evidence; it
is not reopened by this tracking migration.

## Current continuation boundary

The historical manifest Slice 3 surface is closed through `TOOL001-C7`.
`TOOL001-D` is the current bounded continuation for pure release-version and
dependency-constraint value policy. `TOOL001-D1` is CLOSED and `TOOL001-D2` is IN_PROGRESS through closed D2A/D2B;
D2C explicit bounded intervals are READY.

D2 remains pure/local and is explicitly subdivided: D2A exact and D2B caret are
CLOSED; D2C bounded intervals is READY; D2D owns prerelease admission and closure. Candidate selection, lock preservation, lockfile
serialization, workspace policy, package store and registry/network behavior
remain later separately scoped Package Tool work.

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

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
| TOOL001-C | historical manifest Slice 3 parent | IN_PROGRESS | C1/C2 plus schema-v1 design `85ac538d378eeb2153e318145cae69114563e858` | Continue the existing manifest implementation rather than restarting Package Tool under the new family. |

B006's normative prerequisite path through I021 remains historical evidence; it
is not reopened by this tracking migration.

## Current continuation boundary

The next Package Tool implementation work must start by fetching and auditing the
then-current `origin/main`. It should continue the open historical manifest Slice
3 surface from the latest published C1/C2 state rather than recreating bootstrap,
Filesystem provisioning, metadata publication, or already-closed TOML String
behavior.

The package design records identify remaining manifest concerns including full
document/table assembly, manifest schema-v1 validation, project Filesystem reads,
and user-facing diagnostics. Exact subdivision after C2 must be derived from the
current repository and applicable AGENTS rules; this migration intentionally does
not manufacture completion evidence for work not yet published.

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

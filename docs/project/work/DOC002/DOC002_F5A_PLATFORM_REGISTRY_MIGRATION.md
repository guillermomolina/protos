# DOC002-F5A — Platform architecture registry migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `4f8de8b84012c5b24ef22f088e3f41568495a798`.

F5 is mechanically decomposed by reference radius and authority into F5A/F5B/F5C.
F5A migrates:

- `docs/project/PLATFORM_ARCHITECTURE_DECISIONS.md`
  → `docs/project/registries/PLATFORM_ARCHITECTURE_DECISIONS.md`.

## Authority and taxonomy reconciliation

The registry remains durable **non-normative** project documentation. Its 15
PLAT001–PLAT015 rows, statuses, approvals, consumers and decision outcomes are
preserved.

One introductory taxonomy statement was stale after the explicitly ratified
DOC002-F0 Option C: it still equated `Dxxx` with language/specification decisions.
F5A reconciles that wording to F0's already-approved rule that decision role is
orthogonal to identifier family: Dxxx may be language/specification or
tooling/package-system, while observable Protos semantics remain authoritative
under `spec/`.

The same stale pre-F0 wording in `AGENTS.md` is reconciled to that already
ratified policy. This is not a new decision-family design.

## Reference reconciliation

Current active Markdown references to the moved platform-registry path were
reconciled from the execution-time `PUBLICATION_BASE`:

- `AGENTS.md`

Historical DOC002 migration records, retired history, the original DOC002-A
inventory snapshot, CHANGELOG chronology and specification changelog chronology
preserve old path spellings when describing earlier repository state.

Any non-Markdown dependency on the concrete old registry path makes F5A fail
closed.

## Continuation

`DOC002-F4` remains **CLOSED**. `DOC002-F5A` is **CLOSED**. `DOC002-F5` remains
**IN_PROGRESS**. `DOC002-F5B` is **READY** for
`IMPLEMENTATION_BLOCKERS.md`; `DOC002-F5C` remains sequenced after F5B for
`IMPLEMENTATION_STATUS.md`.

No specification, observable semantics, Dxxx/PLATxxx decision outcome, platform
architecture, implementation/runtime behavior, implementation version, public
API, blocker state, or license term changes.

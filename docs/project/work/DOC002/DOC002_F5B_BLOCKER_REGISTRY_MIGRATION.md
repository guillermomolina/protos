# DOC002-F5B — Implementation blocker registry migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `66529941e32062da7a2d54a76904f35bddafd670`.

DOC002-F5B migrates:

- `docs/project/IMPLEMENTATION_BLOCKERS.md`
  → `docs/project/registries/IMPLEMENTATION_BLOCKERS.md`.

## Authority and blocker-state boundary

The blocker ledger remains implementation/governance state, **not a normative
specification**. Normative unblock conditions continue to be checked against the
current ratified material under `spec/` on current `main`.

F5B does not add, remove, reorder or reclassify any Bxxx blocker and does not
change any `BLOCKED`, `READY` or `CLOSED` state. The destination is required to
match the execution-time source except for deterministic path/link rebasing
caused by relocation.

The execution-time source contains `10` distinct Bxxx headings and
`10` blocker `Status:` lines; their exact ordered sequences are
validated unchanged after the move.

## Reference reconciliation

Current active Markdown references to the moved blocker-ledger path were
reconciled:

- `AGENTS.md`
- `docs/guide/README.md`
- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/work/LIB002/LIB002_TEXT_ENCODING_DESIGN.md`
- `docs/project/work/LIB004/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md`
- `src/AGENTS.md`

This may include governance Markdown under `src/`, such as `src/AGENTS.md`;
changing that documentation path does not alter implementation behavior.

The root `AGENTS.md` transitional sentence that cited the legacy blocker path as
an example is retired by F5B and replaced with the canonical registry path.

Historical DOC002 migration records, retired history, the original DOC002-A
inventory snapshot, CHANGELOG chronology and specification changelog chronology
preserve old path spellings when describing earlier repository state.

Any non-Markdown dependency on the concrete old blocker-registry path makes F5B
fail closed.

## Continuation

`DOC002-F5A` remains **CLOSED**. `DOC002-F5B` is **CLOSED**. `DOC002-F5`
remains **IN_PROGRESS**. `DOC002-F5C` is **READY** for the high-reference durable
implementation/closure registry `IMPLEMENTATION_STATUS.md`.

No specification, observable semantics, blocker identity/state/unblock
condition, implementation/runtime behavior, implementation version, public API,
platform decision, or license term changes.

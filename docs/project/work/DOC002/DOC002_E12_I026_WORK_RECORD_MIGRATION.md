# DOC002-E12 — I026 owner-batch work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `d681537ff647622224b4fbc3233004a8dd0f4fd7`.

DOC002-E12 migrates the complete closed flat I026 durable work-record corpus from
the execution-time publication base into the ratified per-owner location
`docs/project/work/I026/`.

## Exact owner batch

- `docs/project/I026_A4B1_TRUFFLE_MULTITHREAD_SAFETY.md` → `docs/project/work/I026/I026_A4B1_TRUFFLE_MULTITHREAD_SAFETY.md`
- `docs/project/I026_A4B2A_PROCESS_CONTEXT_HOSTING.md` → `docs/project/work/I026/I026_A4B2A_PROCESS_CONTEXT_HOSTING.md`
- `docs/project/I026_A4B2B1_ACTOR_CONTEXT_ROUTING.md` → `docs/project/work/I026/I026_A4B2B1_ACTOR_CONTEXT_ROUTING.md`
- `docs/project/I026_A4B2B2_P_CONTEXT_ROUTING.md` → `docs/project/work/I026/I026_A4B2B2_P_CONTEXT_ROUTING.md`
- `docs/project/I026_A4B2B3_CORE_PUBLICATION.md` → `docs/project/work/I026/I026_A4B2B3_CORE_PUBLICATION.md`
- `docs/project/I026_A4B3_DRIVER_CUTOVER.md` → `docs/project/work/I026/I026_A4B3_DRIVER_CUTOVER.md`
- `docs/project/I026_TRUFFLE_TOOLING_FOUNDATION.md` → `docs/project/work/I026/I026_TRUFFLE_TOOLING_FOUNDATION.md`

The execution-time flat `I026_*.md` inventory contained exactly these seven
records. Every record exposed one top-level `Status:` line containing `CLOSED`.
A concurrent eighth flat I026 record, a reopened record, or any missing member
makes this launcher abort rather than silently changing the owner batch.

## Reference reconciliation

Current active Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled:

- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/PLAT001_TRUFFLE_RUNTIME_HOSTING.md`

Relative Markdown links inside all moved records were rebased from their new
directory. No I026 runtime/compiler/tooling behavior, Truffle/Graal integration,
Process/Actor/P semantics, debugger/DAP/LSP contract, public API, specification,
platform decision, or implementation version changes.

Historical chronology, retired snapshots and prior DOC002 migration records
retain old path spellings when those spellings describe earlier repository state.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E13` is **READY**. Every later
work-record batch re-discovers candidates and dependencies from its own
execution-time `PUBLICATION_BASE`; E12 does not freeze the remaining inventory.

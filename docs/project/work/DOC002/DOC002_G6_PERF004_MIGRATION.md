# DOC002-G6 — PERF004 owner-batch migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `c96518d4c1dbb2701179a3843020d39a910c1860`.

DOC002-G6 migrates the complete residual flat PERF004 owner batch:

- `docs/project/PERF004_RUNTIME_PERFORMANCE_CHARACTERIZATION.md`
  → `docs/project/work/PERF004/PERF004_RUNTIME_PERFORMANCE_CHARACTERIZATION.md`.

The execution-time flat `PERF004_*` batch contains exactly this one record, so G6
does not split an owner batch.

## Content, status and authority boundary

The execution-time PERF004 status is `OPEN` and is preserved exactly. G6 does
not start PERF004, move PERF004-A to IN_PROGRESS, release PERF004-B/C, select an
optimization, or change a performance guarantee.

The record remains non-normative performance-engineering project documentation.
It continues to separate benchmark characterization from Protos semantics and
from later optimization decisions. All planned-slice states, material-gap rules,
measurement principles, attribution vocabulary, closure criteria and non-goals
remain those of the execution-time source.

The destination is required to equal the exact execution-time source except for
deterministic Markdown path/link rebasing caused by relocation.

## Active-reference reconciliation

Maintained active Markdown references discovered from the execution-time
`PUBLICATION_BASE` were reconciled:

- none

DOC002 migration/closure evidence, the DOC002-A historical inventory,
CHANGELOG/specification chronology and retired history preserve publication-time
legacy path spellings.

GitHub Issues #52 and #107 are live coordination surfaces and are not mutated by
this repository launcher. Their durable-evidence/authority path references must
be reconciled to the canonical PERF004 path after successful publication.

Any non-Markdown dependency on the concrete old PERF004 path makes G6 fail
closed.

## Remaining G handoff

After removing the PERF004 batch, `4` direct `docs/project/`
residual paths remain:

- `docs/project/TOOL001_F2D_EXECUTION_PREFLIGHT.md`
- `docs/project/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md`
- `docs/project/TOOL001_PACKAGE_TOOL.md`
- `docs/project/TOOL002_TEST_TOOL.md`

`DOC002-G6` is **CLOSED**. `DOC002-G` remains **IN_PROGRESS**.
`DOC002-G7` is **READY** for the complete residual TOOL001 owner batch.

No specification, observable semantics, performance guarantee, PERF004
work/slice/dependency state, benchmark methodology, implementation/runtime
behavior, implementation version, public API, platform architecture,
registry/blocker state, or license term changes.

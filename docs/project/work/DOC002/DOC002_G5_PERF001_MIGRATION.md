# DOC002-G5 — PERF001 owner-batch migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `719a5249f62d989ea5ce3a9da8b03e9c8ba49697`.

DOC002-G5 migrates the complete residual flat PERF001 owner batch:

- `docs/project/PERF001_BENCHMARKING.md`
  → `docs/project/work/PERF001/PERF001_BENCHMARKING.md`
- `docs/project/PERF001_F_CONCURRENCY_METHODOLOGY.md`
  → `docs/project/work/PERF001/PERF001_F_CONCURRENCY_METHODOLOGY.md`

The execution-time flat `PERF001_*` batch contains exactly these two records. G5
moves both together so parent methodology and its concurrency-methodology record
remain one owner batch.

## Content, status and authority boundary

`PERF001_BENCHMARKING.md` remains the non-normative project record for PERF001
benchmark ownership, reproducibility, measurement classes, correctness gates,
cross-language equivalence and retained evidence. It does not define language
semantics or performance guarantees.

`PERF001_F_CONCURRENCY_METHODOLOGY.md` remains a non-normative
performance-engineering methodology/benchmark-contract record. Its exact
execution-time `Status:` line is preserved:

`methodology/workload audit **APPROVED**; canonical workload implementation **PUBLISHED**; companion harness and retained measurement evidence pending`

G5 does not alter any PERF001 A-G slice state, evidence SHA, benchmark workload,
measurement method, dependency, production-entry gate, toolchain identity,
cross-repository publication rule or live GitHub coordination.

The destinations must equal their exact execution-time sources except for
deterministic Markdown path/link rebasing caused by relocation.

## Active-reference reconciliation

Maintained active Markdown references discovered from the execution-time
`PUBLICATION_BASE` were reconciled, including parent/child cross-references and
benchmark-corpus navigation:

- `docs/project/registries/IMPLEMENTATION_STATUS.md`
- `protos/benchmarks/README.md`
- `protos/benchmarks/concurrency/README.md`

DOC002 migration/closure evidence, the DOC002-A historical inventory,
CHANGELOG/specification chronology and retired history preserve publication-time
legacy path spellings.

GitHub Issue #51 is live coordination and is not mutated by this repository
launcher. Its durable-evidence path must be reconciled to the canonical PERF001
path after successful publication.

Any non-Markdown dependency on a concrete old PERF001 path makes G5 fail closed.

## Remaining G handoff

After removing the PERF001 batch, `5` direct `docs/project/`
residual paths remain:

- `docs/project/PERF004_RUNTIME_PERFORMANCE_CHARACTERIZATION.md`
- `docs/project/TOOL001_F2D_EXECUTION_PREFLIGHT.md`
- `docs/project/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md`
- `docs/project/TOOL001_PACKAGE_TOOL.md`
- `docs/project/TOOL002_TEST_TOOL.md`

`DOC002-G5` is **CLOSED**. `DOC002-G` remains **IN_PROGRESS**.
`DOC002-G6` is **READY** for the residual PERF004 owner batch.

No specification, observable semantics, performance guarantee, PERF001
work/slice/dependency/evidence state, benchmark methodology, implementation/runtime
behavior, implementation version, public API, platform architecture,
registry/blocker state, or license term changes.

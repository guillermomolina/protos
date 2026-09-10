# DOC002-G2 — DOC001 owner-batch migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `ca19ed06c7c71da1a1a774b1673d0d6fc5d188d8`.

DOC002-G2 migrates the complete residual flat DOC001 owner batch:

- `docs/project/DOC001_PROGRAMMING_DOCUMENTATION.md`
  → `docs/project/work/DOC001/DOC001_PROGRAMMING_DOCUMENTATION.md`.

The execution-time flat `DOC001_*` batch contains exactly this one record, so no
active DOC001 owner batch is split.

## Content and authority boundary

The record remains `IN_PROGRESS`, remains the canonical project record for
DOC001, and remains explicitly non-normative. The normative Protos language and
Standard Library authority remains under `spec/`.

G2 changes no DOC001 slice status, dependency, closure evidence, completion rule,
programming-guide content or toolchain blocker. The destination must equal the
execution-time source except for deterministic rebasing of relative Markdown
links caused by relocation.

## Active-reference reconciliation

Maintained active Markdown references discovered from the execution-time
`PUBLICATION_BASE` were reconciled:

- `AGENTS.md`
- `docs/guide/README.md`
- `docs/project/registries/IMPLEMENTATION_STATUS.md`

DOC002 migration/closure evidence, the DOC002-A historical inventory,
CHANGELOG/specification chronology and retired history preserve publication-time
legacy path spellings.

Any non-Markdown dependency on the concrete old DOC001 path makes G2 fail closed.

## Remaining G handoff

After removing the DOC001 batch, `12` direct `docs/project/`
residual paths remain:

- `docs/project/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md`
- `docs/project/LM008_B_GRAMMAR_EVALUATION_BINDING_CALLABLE_AUDIT.md`
- `docs/project/LM008_CORE_LANGUAGE_SURFACE_COMPLETENESS.md`
- `docs/project/LM008_C_OBJECT_STRUCTURAL_REFLECTION_MUTATION_AUDIT.md`
- `docs/project/LM008_D_VALUES_CORE_COLLECTIONS_AUDIT.md`
- `docs/project/PERF001_BENCHMARKING.md`
- `docs/project/PERF001_F_CONCURRENCY_METHODOLOGY.md`
- `docs/project/PERF004_RUNTIME_PERFORMANCE_CHARACTERIZATION.md`
- `docs/project/TOOL001_F2D_EXECUTION_PREFLIGHT.md`
- `docs/project/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md`
- `docs/project/TOOL001_PACKAGE_TOOL.md`
- `docs/project/TOOL002_TEST_TOOL.md`

`DOC002-G2` is **CLOSED**. `DOC002-G` remains **IN_PROGRESS**.
`DOC002-G3` is **READY** for the residual AUD003 owner batch.

No specification, observable semantics, DOC001 work/slice state, blocker or
dependency state, implementation/runtime behavior, implementation version,
public API, platform architecture, registry meaning, or license term changes.

# DOC002-G3 — AUD003 owner-batch migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `3a92521fa492441070dd243cfb23c04cc370fc17`.

DOC002-G3 migrates the complete residual flat AUD003 owner batch:

- `docs/project/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md`
  → `docs/project/work/AUD003/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md`.

The execution-time flat `AUD003_*` batch contains exactly this one record. G3
does not split the active owner batch; any already-canonical AUD003 companion
records may coexist under `work/AUD003/`.

## Content and authority boundary

The execution-time AUD003 status is `IN_PROGRESS` and is preserved exactly. G3 does
not assume that AUD003 must remain open while this documentation migration runs:
concurrent AUD003 work may legitimately advance before invocation.

The record remains a non-normative repository source-style audit. Its policy
authority remains `docs/guide/SOURCE_STYLE.md` plus the repository-level
`AGENTS.md` source-style rule. G3 does not reopen source-style policy, select new
syntax/semantics, alter slice outcomes, or modify exception/migration evidence.

The destination is required to equal the exact execution-time source except for
deterministic rebasing of relative Markdown links and replacement of any
maintained concrete self-path with the canonical path.

## Active-reference reconciliation

Maintained active Markdown references discovered from the execution-time
`PUBLICATION_BASE` were reconciled:

- `docs/project/registries/IMPLEMENTATION_STATUS.md`

DOC002 migration/closure evidence, the DOC002-A historical inventory,
CHANGELOG/specification chronology and retired history preserve publication-time
legacy path spellings.

Any non-Markdown dependency on the concrete old AUD003 path makes G3 fail closed.

## Remaining G handoff

After removing the AUD003 batch, `11` direct `docs/project/`
residual paths remain:

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

`DOC002-G3` is **CLOSED**. `DOC002-G` remains **IN_PROGRESS**.
`DOC002-G4` is **READY** for the residual LM008 owner batch.

No specification, observable semantics, AUD003 policy/slice/work-item state,
source-style exception meaning, implementation/runtime behavior, implementation
version, public API, platform architecture, registry meaning, blocker state, or
license term changes.

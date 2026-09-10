# DOC002-G1 — DOC002-A historical audit migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `a7ee22523652ddbaa0f29acbe4f224bac23344c6`.

DOC002-G1 migrates the single residual flat DOC002 owner-batch record:

- `docs/project/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md`
  → `docs/project/work/DOC002/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md`.

The execution-time flat `DOC002_*` owner batch contains exactly that one record,
so the move does not split an owner batch.

## Historical-content boundary

DOC002-A is the 107-file historical inventory and the retained Option A decision
packet. Its inventory rows, publication-time path literals, counts, alternatives
and approval-gate wording are preserved exactly. The move rebases only relative
Markdown links so current navigation from the relocated file still resolves.

This remains non-normative project documentation. It changes neither the
ratified Option A path contract nor any Protos semantics.

## Active-reference reconciliation

Maintained active Markdown references discovered from the execution-time
`PUBLICATION_BASE` were reconciled:

- `docs/README.md`
- `docs/project/README.md`
- `docs/project/work/DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md`

Prior DOC002 migration/ratification/closure records, CHANGELOG chronology,
retired history and specification changelog chronology preserve old path
spellings when they describe publication-time repository state.

Any non-Markdown dependency on the old concrete path makes G1 fail closed.

## Remaining G handoff

After removing this DOC002 owner batch, `13` direct
`docs/project/` residual paths remain for later G owner-batch migration and final
closing rescan:

- `docs/project/AUD003_PROTOS_SOURCE_STYLE_CONFORMANCE_AUDIT.md`
- `docs/project/DOC001_PROGRAMMING_DOCUMENTATION.md`
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

`DOC002-G1` is **CLOSED**. `DOC002-G` remains **IN_PROGRESS**.
`DOC002-G2` is **READY** for the residual DOC001 owner batch.

No specification, observable semantics, DOC002 decision outcome, work-item state,
implementation/runtime behavior, implementation version, public API, platform
architecture, registry content, blocker state, or license term changes.

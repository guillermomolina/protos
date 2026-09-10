# DOC002-G4 — LM008 owner-batch migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `a65a00cd570d119648eb3398685ecd5058b58d83`.

DOC002-G4 migrates the complete residual flat LM008 owner batch:

- `docs/project/LM008_B_GRAMMAR_EVALUATION_BINDING_CALLABLE_AUDIT.md`
  → `docs/project/work/LM008/LM008_B_GRAMMAR_EVALUATION_BINDING_CALLABLE_AUDIT.md`
- `docs/project/LM008_CORE_LANGUAGE_SURFACE_COMPLETENESS.md`
  → `docs/project/work/LM008/LM008_CORE_LANGUAGE_SURFACE_COMPLETENESS.md`
- `docs/project/LM008_C_OBJECT_STRUCTURAL_REFLECTION_MUTATION_AUDIT.md`
  → `docs/project/work/LM008/LM008_C_OBJECT_STRUCTURAL_REFLECTION_MUTATION_AUDIT.md`
- `docs/project/LM008_D_VALUES_CORE_COLLECTIONS_AUDIT.md`
  → `docs/project/work/LM008/LM008_D_VALUES_CORE_COLLECTIONS_AUDIT.md`

The execution-time flat `LM008_*` batch contains exactly these four records. G4
moves all four together so the active owner batch is never split.

## Content, status and authority boundary

Execution-time statuses, all preserved exactly:

- `LM008_CORE_LANGUAGE_SURFACE_COMPLETENESS.md` → `IN_PROGRESS`
- `LM008_B_GRAMMAR_EVALUATION_BINDING_CALLABLE_AUDIT.md` → `CLOSED`
- `LM008_C_OBJECT_STRUCTURAL_REFLECTION_MUTATION_AUDIT.md` → `BLOCKED_BY_DEPENDENCIES`
- `LM008_D_VALUES_CORE_COLLECTIONS_AUDIT.md` → `IN_PROGRESS`

G4 deliberately does not require a particular parent/child status. LM008 may
advance concurrently before this launcher executes; each record's status is read
from `PUBLICATION_BASE` and must remain byte-equivalent after deterministic
path/link rebasing.

LM008 remains a non-normative Core-language surface maturity audit. It does not
define new language behavior. LM008-B/C/D remain non-normative audit/evidence
records, and any genuinely unresolved semantic choice still belongs to the
ordinary Dxxx approval gate rather than DOC002.

No LM008 classification, implementation-gap owner, dependency, evidence row,
decision gate or closure criterion is altered.

## Reference reconciliation

Maintained active Markdown references discovered from the execution-time
`PUBLICATION_BASE` were reconciled, including cross-references among the four
moved LM008 records:

- `docs/project/registries/IMPLEMENTATION_STATUS.md`

DOC002 migration/closure evidence, the DOC002-A historical inventory,
CHANGELOG/specification chronology and retired history preserve publication-time
legacy path spellings.

Any non-Markdown dependency on a concrete old LM008 path makes G4 fail closed.

## Remaining G handoff

After removing the LM008 batch, `7` direct `docs/project/`
residual paths remain:

- `docs/project/PERF001_BENCHMARKING.md`
- `docs/project/PERF001_F_CONCURRENCY_METHODOLOGY.md`
- `docs/project/PERF004_RUNTIME_PERFORMANCE_CHARACTERIZATION.md`
- `docs/project/TOOL001_F2D_EXECUTION_PREFLIGHT.md`
- `docs/project/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md`
- `docs/project/TOOL001_PACKAGE_TOOL.md`
- `docs/project/TOOL002_TEST_TOOL.md`

`DOC002-G4` is **CLOSED**. `DOC002-G` remains **IN_PROGRESS**.
`DOC002-G5` is **READY** for the residual PERF001 owner batch.

No specification, observable semantics, LM008 work/slice/dependency state,
maturity classification, implementation/runtime behavior, implementation
version, public API, platform architecture, registry/blocker state, or license
term changes.

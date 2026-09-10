# DOC002-G7 — TOOL001 owner-batch migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `f76ba38e97c6705a95c3c46068f0a82801c6c531`.

DOC002-G7 migrates the complete residual flat TOOL001 owner batch:

- `docs/project/TOOL001_F2D_EXECUTION_PREFLIGHT.md`
  → `docs/project/work/TOOL001/TOOL001_F2D_EXECUTION_PREFLIGHT.md`
- `docs/project/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md`
  → `docs/project/work/TOOL001/TOOL001_F2E_EXTERNAL_MATERIALIZATION.md`
- `docs/project/TOOL001_PACKAGE_TOOL.md`
  → `docs/project/work/TOOL001/TOOL001_PACKAGE_TOOL.md`

The execution-time flat `TOOL001_*` batch contains exactly these three records.
G7 moves all three together so the active Package Tool owner batch is never
split.

## Content, status and authority boundary

Execution-time `Status:` lines, all preserved exactly:

- `TOOL001_PACKAGE_TOOL.md` → `IN_PROGRESS`
- `TOOL001_F2D_EXECUTION_PREFLIGHT.md` → `**CLOSED — workspace-only execution preflight and public exact workspace run**`
- `TOOL001_F2E_EXTERNAL_MATERIALIZATION.md` → `**IN_PROGRESS — F2E1/F2E2/F2E3 CLOSED; F2E4 READY; F2E5 dependency-gated**`

G7 deliberately does not require a specific TOOL001/F2D/F2E progress state.
Concurrent Package Tool work may advance before this launcher executes; the
exact execution-time content is authoritative for this mechanical relocation.

The parent remains a non-normative project implementation record. F2D remains
the non-normative workspace-only PackageExecutionPlan/preflight host-integration
record. F2E remains the non-normative external immutable-package execution
continuation. G7 changes no package semantics, Dxxx/PLATxxx decision, lifecycle
state, dependency, evidence SHA, capability boundary, authority separation,
ContentIdentity rule, PackageExecutionPlan ABI, resolver rule or public run
behavior.

Each destination must equal its exact execution-time source except for
deterministic Markdown path/link rebasing caused by relocation.

## Active-reference reconciliation

Maintained active Markdown references discovered from the execution-time
`PUBLICATION_BASE` were reconciled, including cross-references among TOOL001
records and current package/design/implementation documentation:

- `docs/design/PACKAGE_CONTENT_IDENTITY.md`
- `docs/project/registries/IMPLEMENTATION_STATUS.md`
- `docs/project/work/LIB006/LIB006_HASHING_DESIGN.md`

DOC002 migration/closure evidence, the DOC002-A historical inventory,
CHANGELOG/specification chronology and retired history preserve publication-time
legacy path spellings.

`scripts/test_validation_impact.py` is the one audited active non-Markdown
compatibility consumer. Its `test_package_with_docs_is_still_local` fixture
continues to exercise the same `TOOL_LOCAL:PACKAGE` rule, but its representative
neutral documentation path follows the canonical TOOL001 parent location.
`scripts/validation_impact.py` itself is unchanged.

GitHub Issue #47 and any durable child Issue still citing the old TOOL001 paths
are live/presentation coordination surfaces and are not mutated by this launcher.
Their concrete path references must be reconciled after successful publication.

Any other non-Markdown dependency on a concrete old TOOL001 documentation path
makes G7 fail closed.

## Remaining G handoff

After removing the TOOL001 batch, `1` direct `docs/project/`
residual paths remain:

- `docs/project/TOOL002_TEST_TOOL.md`

`DOC002-G7` is **CLOSED**. `DOC002-G` remains **IN_PROGRESS**.
`DOC002-G8` is **READY** for the residual TOOL002 owner batch.

No specification, observable semantics, Package Tool architecture/decision,
TOOL001 work/slice/dependency/evidence state, implementation/runtime behavior,
implementation version, public API, platform architecture, registry/blocker
state, or license term changes.

# DOC002-F5C — Durable implementation/closure registry migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `89c259163f698bc699f475070c254aebe07343da`.

DOC002-F5C migrates the final legacy high-value registry:

- `docs/project/IMPLEMENTATION_STATUS.md`
  → `docs/project/registries/IMPLEMENTATION_STATUS.md`.

## Authority boundary

The registry preserves durable implementation inventory, historical publication
state and closure evidence. It remains explicitly **not** the live
repository-level status, scheduling, readiness, priority, assignment or blocking
view.

Live actionable coordination remains in GitHub Issues and live scheduling/status
remains in the Protos Development Project. Normative language authority remains
under `spec/`; the durable Bxxx unblock-condition ledger remains
`docs/project/registries/IMPLEMENTATION_BLOCKERS.md`.

F5C does not reinterpret, add, remove, reorder, close or reopen any formal work
item. The migrated registry is required to match its exact execution-time source
except for deterministic relative Markdown-link rebasing and replacement of the
registry's own concrete legacy path with its canonical location.

## Reference reconciliation

Current active Markdown references to the moved implementation-registry path were
reconciled from the execution-time `PUBLICATION_BASE`:

- `AGENTS.md`
- `README.md`
- `ROADMAP.md`
- `docs/design/PACKAGE_TOOL_ARCHITECTURE.md`
- `docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md`
- `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`
- `docs/guide/10-actors-actorrefs-and-groups.md`
- `docs/guide/11-process-io-filesystems-and-authority.md`
- `docs/guide/README.md`
- `docs/project/PERF001_BENCHMARKING.md`
- `docs/project/TOOL001_PACKAGE_TOOL.md`
- `docs/project/TOOL002_TEST_TOOL.md`
- `docs/project/work/AUD002/AUD002_GRAALVM_EDITOR_TOOLING_AUDIT.md`
- `docs/project/work/I026/I026_A4B3_DRIVER_CUTOVER.md`
- `docs/project/work/LIB001/LIB001_COLLECTIONS_DESIGN.md`
- `docs/project/work/LIB002/LIB002_TEXT_ENCODING_DESIGN.md`
- `docs/project/work/LIB004/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md`
- `docs/project/work/LIB006/LIB006_HASHING_DESIGN.md`
- `docs/project/work/WEB001/WEB001_PROJECT_WEBSITE.md`

This intentionally includes high-visibility navigation such as root README,
ROADMAP, AGENTS, guide/design documents and active work records whenever present
at execution time.

Historical DOC002 migration records, retired history, the original DOC002-A
inventory snapshot, CHANGELOG chronology and specification changelog chronology
preserve old path spellings when describing earlier repository state.

Any non-Markdown dependency on the concrete old implementation-registry path
makes F5C fail closed instead of silently changing a compatibility contract.

## Phase transition

`DOC002-F5A`, `DOC002-F5B`, and `DOC002-F5C` are **CLOSED**. `DOC002-F5` is
**CLOSED**. `DOC002-F` remains **IN_PROGRESS** and `DOC002-F6` is **READY** for
an execution-time phase-F closure audit.

F6 must verify that no residual legacy Dxxx, PLATxxx, CORE_* or high-value
registry path remains outside its selected role and must re-inventory concurrent
stragglers before allowing DOC002-F to close and DOC002-G to become ready.

No specification, observable semantics, work-item state/closure evidence,
blocking state, platform decision, implementation/runtime behavior,
implementation version, public API, or license term changes.

# DOC002-G8 — TOOL002 owner-batch migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `d4035ef835e292402da4c2f8e9f5093928935e3b`.

DOC002-G8 migrates the complete residual flat TOOL002 owner batch:

- `docs/project/TOOL002_TEST_TOOL.md`
  → `docs/project/work/TOOL002/TOOL002_TEST_TOOL.md`.

The execution-time flat `TOOL002_*` owner batch contains exactly this one record.

## Content, status and authority boundary

The exact execution-time TOOL002 Status line is preserved:

`IN_PROGRESS`

The record remains a non-normative project implementation record. G8 does not
resume suspended TOOL002-H scheduling, advance H/I/J, select a jobs/resource/
timeout/worker policy, alter Test Tool architecture, or change Protos semantics.

The destination is required to equal the execution-time source except for
deterministic path/link rebasing caused by relocation.

## Active references and test fixture

Maintained active Markdown references discovered from the execution-time
`PUBLICATION_BASE` were reconciled:

- `docs/design/TEST_TOOL_COMPARATIVE_AUDIT.md`
- `docs/design/TEST_TOOL_SCALE_AND_DISTRIBUTION_ARCHITECTURE.md`
- `docs/project/registries/IMPLEMENTATION_STATUS.md`

`scripts/test_validation_impact.py` is the one audited active non-Markdown
consumer. Its `test_normal_name_status_parser` fixture contains the TOOL002 path
both in synthetic `git diff --name-status -z` input and in expected parser output.
Both literals follow the canonical TOOL002 path together. The parser behavior and
`scripts/validation_impact.py` are unchanged.

DOC002 migration/closure evidence, DOC002-A historical inventory,
CHANGELOG/specification chronology and retired history preserve publication-time
legacy spellings.

Any other non-Markdown old-path dependency makes G8 fail closed.

## Final G handoff

After the TOOL002 move, `0` direct `docs/project/` residual paths
remain at this execution-time base:

- none

Zero residuals here does not close DOC002. `DOC002-G8` is **CLOSED** and
`DOC002-G` remains **IN_PROGRESS**. `DOC002-G9` is **READY** for the mandatory
execution-time final rescan, navigation reconciliation, live-GitHub path check and
DOC002 closure audit. Any concurrent straggler discovered by G9 must be accounted
for before closure.

No specification, observable semantics, Test Tool architecture/decision,
TOOL002 work/slice/dependency/evidence state, implementation/runtime behavior,
implementation version, public API, platform architecture, registry/blocker
state, or license term changes.

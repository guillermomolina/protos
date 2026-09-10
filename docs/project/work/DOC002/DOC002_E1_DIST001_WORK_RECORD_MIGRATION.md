# DOC002-E1 — DIST001 maintained work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `b638067284c6598d06d6d99e73beb874beba482e`.

DOC002-E1 begins the formal work-item migration phase with the closed/inactive
DIST001 owner so the per-owner `docs/project/work/<formal-work-item>/` pattern is
validated before active families are migrated.

Moved maintained records:

- `docs/project/DIST001_FIRST_PRERELEASE_READINESS.md` → `docs/project/work/DIST001/DIST001_FIRST_PRERELEASE_READINESS.md`
- `docs/project/DIST001_PRERELEASE_VERSION_CONTRACT.md` → `docs/project/work/DIST001/DIST001_PRERELEASE_VERSION_CONTRACT.md`
- `docs/project/DIST001_RELEASE_POLICY.md` → `docs/project/work/DIST001/DIST001_RELEASE_POLICY.md`

The seven immutable DIST001 release-evidence blobs remain separately under
`docs/project/evidence/DIST001/` and are not modified by E1.

Current Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled to the new paths:

- `AGENTS.md`
- `ROADMAP.md`
- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/work/DOC002/DOC002_D3_EVIDENCE_MIGRATION.md`

Relative links inside the moved records were rebased from their new directory
without otherwise changing their project/release-engineering meaning.
`CHANGELOG.md`, DOC002-A, `spec/PROTOS_SPEC_CHANGELOG.md`, and immutable evidence
retain older path spellings where those spellings are historical data.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E2` is **READY** for the next bounded
formal-work-record batch. Every later batch must re-inventory its own
execution-time `PUBLICATION_BASE`; E1 is not a frozen migration manifest.

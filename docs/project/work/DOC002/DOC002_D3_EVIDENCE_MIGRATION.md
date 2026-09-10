# DOC002-D3 — Immutable DIST001 evidence migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `b36ee786726fb1303ffeae8c42a2f00ec547fe8b`.

DOC002-D3 closes the low-risk governance/history/evidence migration phase by
moving the seven flat immutable DIST001 release-evidence records into
`docs/project/evidence/DIST001/`.

Every evidence blob is preserved byte-for-byte. Embedded legacy paths and hashes
inside those records are historical data and are intentionally not rewritten.
Current Markdown references and the bounded `dist/*.py` release-tool consumers
discovered from this invocation's `PUBLICATION_BASE` are reconciled atomically.
Fenced command examples are updated; fenced `key=value` blocks that reproduce
historical persisted records remain literal historical data.

Moved records:

- `docs/project/DIST001_E4_CANDIDATE_ARTIFACT.txt` → `docs/project/evidence/DIST001/DIST001_E4_CANDIDATE_ARTIFACT.txt`
- `docs/project/DIST001_E4_CANDIDATE_AUDIT.txt` → `docs/project/evidence/DIST001/DIST001_E4_CANDIDATE_AUDIT.txt`
- `docs/project/DIST001_E4_RELEASE_CLAIMS.txt` → `docs/project/evidence/DIST001/DIST001_E4_RELEASE_CLAIMS.txt`
- `docs/project/DIST001_E4_RELEASE_ENVELOPE.txt` → `docs/project/evidence/DIST001/DIST001_E4_RELEASE_ENVELOPE.txt`
- `docs/project/DIST001_E4_SELECTION.txt` → `docs/project/evidence/DIST001/DIST001_E4_SELECTION.txt`
- `docs/project/DIST001_E4_VALIDATION.txt` → `docs/project/evidence/DIST001/DIST001_E4_VALIDATION.txt`
- `docs/project/DIST001_FIRST_PRERELEASE_PUBLICATION.txt` → `docs/project/evidence/DIST001/DIST001_FIRST_PRERELEASE_PUBLICATION.txt`

Operational release-tool/test paths rewritten:

- `dist/commit_release_candidate.py`
- `dist/materialize_release_candidate.py`
- `dist/prepare_release_candidate_worktree.py`
- `dist/test_commit_release_candidate.py`
- `dist/test_materialize_release_candidate.py`
- `dist/test_prepare_release_candidate_worktree.py`
- `dist/test_transition_release_candidate_version.py`
- `dist/test_verify_candidate_archive_identity.py`
- `dist/test_verify_release_candidate_lineage.py`
- `dist/transition_release_candidate_version.py`
- `dist/verify_release_candidate_lineage.py`

Current reference-bearing Markdown files rewritten:

- `dist/README.md`
- `docs/project/DIST001_PRERELEASE_VERSION_CONTRACT.md`
- `docs/project/DIST001_RELEASE_POLICY.md`
- `docs/project/IMPLEMENTATION_STATUS.md`

The maintained DIST001 `.md` policy/readiness/version-contract records are not
evidence; they remain at their current legacy paths for the later DOC002
work-record migration.

`CHANGELOG.md`, DOC002-A, and `spec/PROTOS_SPEC_CHANGELOG.md` retain older path
spellings where those spellings are part of historical chronology/audit evidence.

## Continuation

`DOC002-D` is **CLOSED**. `DOC002-E` is **READY** and owns formal work-item
records. It must derive its candidate set from its own execution-time
`PUBLICATION_BASE`; this D3 manifest is not a frozen list for later slices.

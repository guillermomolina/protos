# DOC002-E8 — PERF002 work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `4e4c0a55b878d6cf4cfebd81722fd8f6432618fb`.

DOC002-E8 moves the closed PERF002 Truffle-compilability durable work record into
its ratified per-owner location:

- `docs/project/PERF002_TRUFFLE_COMPILABILITY.md` →
  `docs/project/work/PERF002/PERF002_TRUFFLE_COMPILABILITY.md`

The execution-time flat `PERF002_*.md` inventory contained exactly this one
record. A concurrent additional flat PERF002 record makes this launcher abort
rather than silently extending the slice.

Current active Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled:

- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/work/DIST001/DIST001_RELEASE_POLICY.md`

Relative Markdown links inside the moved record are rebased from the new
directory. No Truffle optimization implementation, compiler/runtime behavior,
benchmark evidence, Protos semantics, performance guarantee, build/release
contract, public API, or implementation version changes.

Historical chronology and migration records retain old path spellings when
those spellings describe earlier repository state. In addition,
`dist/verify_candidate_archive_identity.py` and its matching test intentionally
retain the old PERF002 path because DIST001-E4C2 verifies the exact historical
`RUNTIME.txt` contract embedded in the already-persisted `0.2.236` candidate
archive. Those literals are compatibility evidence, not current document
locators, and changing them would falsify the historical archive check.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E9` is **READY**. Every later
work-record batch re-discovers candidates and dependencies from its own
execution-time `PUBLICATION_BASE`; E8 does not freeze the remaining inventory.

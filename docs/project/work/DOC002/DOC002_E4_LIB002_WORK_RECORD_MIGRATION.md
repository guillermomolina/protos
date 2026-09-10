# DOC002-E4 — LIB002 work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `21b5d2fb82dcd99a961dd02eb969cd4fd43df408`.

DOC002-E4 moves the closed initial-scope LIB002 durable design record into its
ratified per-owner location:

- `docs/project/LIB002_TEXT_ENCODING_DESIGN.md` →
  `docs/project/work/LIB002/LIB002_TEXT_ENCODING_DESIGN.md`

The execution-time flat `LIB002_*.md` inventory contained exactly this one
record. A concurrent additional flat LIB002 record makes this launcher abort
rather than silently extending the slice.

Current active Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled:

- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md`

Relative Markdown links inside the moved record are rebased from the new
directory. No Encoding/Text I/O semantics, Core/Standard-Library boundary,
public API, implementation/runtime behavior, or implementation version changes.

Historical chronology and migration records retain old path spellings when those
spellings describe earlier repository state.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E5` is **READY**. Every later
work-record batch re-discovers candidates and dependencies from its own
execution-time `PUBLICATION_BASE`; E4 does not freeze the remaining inventory.

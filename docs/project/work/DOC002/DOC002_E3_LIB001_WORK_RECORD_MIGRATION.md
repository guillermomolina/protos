# DOC002-E3 — LIB001 work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `78be7978aeaee696793d0e92d33bc9959e5ad202`.

DOC002-E3 moves the implementation-complete LIB001 durable design record into
its ratified per-owner location:

- `docs/project/LIB001_COLLECTIONS_DESIGN.md` →
  `docs/project/work/LIB001/LIB001_COLLECTIONS_DESIGN.md`

The execution-time flat `LIB001_*.md` inventory contained exactly this one
record. A concurrent additional flat LIB001 record would make this launcher
abort rather than extending the slice implicitly.

Current active Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled:

- `docs/design/STRUCTURED_DATA_AND_SERIALIZATION.md`
- `docs/project/IMPLEMENTATION_STATUS.md`
- `docs/project/LIB002_TEXT_ENCODING_DESIGN.md`
- `docs/project/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md`

Relative Markdown links inside the moved record are rebased from the new
directory. No collection semantics, Core/Standard-Library boundary, API,
implementation/runtime behavior, or implementation version changes.

Historical chronology and migration records retain old path spellings when those
spellings describe earlier repository state.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E4` is **READY**. Every later
work-record batch re-discovers candidates and dependencies from its own
execution-time `PUBLICATION_BASE`; E3 does not freeze the remaining inventory.

# DOC002-E11 — LIB004 work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `08f2795544aeb888f36910990213bfd721f5da38`.

DOC002-E11 moves the design-closed LIB004 Filesystem / Process Conveniences
durable work record into its ratified per-owner location:

- `docs/project/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md` →
  `docs/project/work/LIB004/LIB004_FILESYSTEM_PROCESS_CONVENIENCES_DESIGN.md`

The execution-time flat `LIB004_*.md` inventory contained exactly this one
record. A concurrent additional flat LIB004 record makes this launcher abort
rather than silently extending the slice.

Current active Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled:

- `docs/project/IMPLEMENTATION_STATUS.md`

Relative Markdown links inside the moved record are rebased from the new
directory. No filesystem/process convenience API, Path/File/Filesystem/Process,
Future/Actor, cleanup/cancellation/transfer/module semantics, runtime
implementation, public API, or implementation version changes.

Historical chronology and migration records retain old path spellings when
those spellings describe earlier repository state.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E12` is **READY**. Every later
work-record batch re-discovers candidates and dependencies from its own
execution-time `PUBLICATION_BASE`; E11 does not freeze the remaining inventory.

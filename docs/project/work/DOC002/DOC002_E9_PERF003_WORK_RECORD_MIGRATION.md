# DOC002-E9 — PERF003 work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `679c4da5dd525d9f26f8a55f370f421b38dc244a`.

DOC002-E9 moves the closed PERF003 collection-algorithm Truffle-compilability
durable work record into its ratified per-owner location:

- `docs/project/PERF003_COLLECTION_COMPILABILITY.md` →
  `docs/project/work/PERF003/PERF003_COLLECTION_COMPILABILITY.md`

The execution-time flat `PERF003_*.md` inventory contained exactly this one
record. A concurrent additional flat PERF003 record makes this launcher abort
rather than silently extending the slice.

Current active Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled:

- `docs/project/IMPLEMENTATION_STATUS.md`

Relative Markdown links inside the moved record are rebased from the new
directory. No collection algorithm, Truffle optimization implementation,
compiler/runtime behavior, benchmark evidence, Protos or Standard Library
semantics, performance guarantee, public API, or implementation version changes.

Historical chronology and migration records retain old path spellings when
those spellings describe earlier repository state.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E10` is **READY**. Every later
work-record batch re-discovers candidates and dependencies from its own
execution-time `PUBLICATION_BASE`; E9 does not freeze the remaining inventory.

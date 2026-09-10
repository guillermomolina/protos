# DOC002-E6 — LIB006 work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `267a8ecfca78d57780e8f66a40668e00f347b0ee`.

DOC002-E6 moves the closed LIB006 deterministic-hashing durable work record into
its ratified per-owner location:

- `docs/project/LIB006_HASHING_DESIGN.md` →
  `docs/project/work/LIB006/LIB006_HASHING_DESIGN.md`

The execution-time flat `LIB006_*.md` inventory contained exactly this one
record. A concurrent additional flat LIB006 record makes this launcher abort
rather than silently extending the slice.

Current active Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled:

- none

Relative Markdown links inside the moved record are rebased from the new
directory. No SHA-256 API/algorithm semantics, Standard Library behavior,
implementation/runtime behavior, security contract, Package Tool semantics, or
implementation version changes.

Historical chronology and migration records retain old path spellings when those
spellings describe earlier repository state.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E7` is **READY**. Every later
work-record batch re-discovers candidates and dependencies from its own
execution-time `PUBLICATION_BASE`; E6 does not freeze the remaining inventory.

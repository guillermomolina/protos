# DOC002-E13 — I028 work-record migration

Status: **CLOSED**

Owning live work item: GitHub Issue `#156` — `DOC002 — Documentation information
architecture and repository reorganization`.

Execution-time publication base: `5b497305df4445d93500030d577340f693051f9e`.

DOC002-E13 moves the closed I028 Core Networking Foundation durable work record
into its ratified per-owner location:

- `docs/project/I028_NETWORKING_FOUNDATION.md` →
  `docs/project/work/I028/I028_NETWORKING_FOUNDATION.md`

The execution-time flat `I028_*.md` inventory contained exactly this one record,
and its top-level status was `CLOSED`. A concurrent additional flat I028 record,
a reopened I028 record, or a pre-existing `work/I028/` owner directory makes the
launcher abort rather than silently changing the migration boundary.

## Reference reconciliation

Current active Markdown references discovered from this invocation's
`PUBLICATION_BASE` were reconciled:

- `docs/project/IMPLEMENTATION_STATUS.md`

Relative Markdown links inside the moved record are rebased from its new
directory. No networking semantics, IpAddress/IpEndpoint contract, TCP resource
topology, Process/Network capability, host-I/O architecture, LIB005 design,
runtime implementation, public API, specification, platform decision, or
implementation version changes.

Historical chronology, the DOC002-A inventory snapshot, retired history and
prior DOC002 migration records retain old path spellings when those spellings
describe earlier repository state.

## Continuation

`DOC002-E` remains **IN_PROGRESS**. `DOC002-E14` is **READY**. Every later
work-record batch re-discovers candidates and dependencies from its own
execution-time `PUBLICATION_BASE`; E13 does not freeze the remaining inventory.

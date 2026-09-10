# Protos project documentation

This directory contains durable, non-normative project documentation. It does
not define observable Protos semantics: normative language and Standard Library
semantics remain under `spec/`. GitHub Issues and the Protos Development Project
remain the live coordination, scheduling, assignment, and priority surfaces.

DOC002 selected a **role-first** information architecture. New durable records
use the role that describes what the document is; a formal work identifier is a
separate ownership axis and is not invented merely to make the tree symmetrical.

## Role navigation

- [`work/`](work/README.md) — durable records primarily owned by one formal work
  item, grouped by the individual identifier.
- [`decisions/`](decisions/README.md) — durable non-normative decision records,
  split into [language](decisions/language/README.md) and
  [platform](decisions/platform/README.md) roles.
- [`architecture/`](architecture/README.md) — cross-cutting implementation
  architecture not primarily owned by one ordinary work item.
- [`governance/`](governance/README.md) — maintained repository/project policy
  and rationale that does not need an invented formal work family.
- [`registries/`](registries/README.md) — durable registries and closure/evidence
  ledgers; never a replacement for live GitHub work state.
- [`evidence/`](evidence/README.md) — immutable or snapshot-like evidence,
  grouped by genuine formal owner when one exists.
- [`history/`](history/README.md) — retired or superseded repository snapshots
  kept for historical value.

## DOC002 architecture authority

The ratified path and compatibility rules are in
[`work/DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md`](work/DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md).
The original inventory and alternatives remain in the historical DOC002-A audit
at [`DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md`](DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md).
The navigation-foundation closure record is
[`work/DOC002/DOC002_NAVIGATION_FOUNDATION.md`](work/DOC002/DOC002_NAVIGATION_FOUNDATION.md).

## Transitional legacy paths

Many durable records still live directly under `docs/project/`. Those paths are
intentional compatibility locations during staged migration, not examples for new
document placement. Existing legacy documents are edited at their actual current
paths until a bounded DOC002 migration owns their relocation. Do not duplicate,
opportunistically move, or guess the future path of a legacy record.

DOC002 migration slices discover candidates from their execution-time
`PUBLICATION_BASE`. Before DOC002 closes, current `docs/project/` is re-inventoried
and every residual flat legacy/straggler path is either migrated or explicitly
retained for a documented compatibility reason.

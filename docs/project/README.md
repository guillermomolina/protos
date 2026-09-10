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
  split into [language](decisions/language/README.md),
  [tooling](decisions/tooling/README.md), and
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
at [`DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md`](work/DOC002/DOC002_DOCUMENTATION_ARCHITECTURE_AUDIT.md).
The navigation-foundation closure record is
[`work/DOC002/DOC002_NAVIGATION_FOUNDATION.md`](work/DOC002/DOC002_NAVIGATION_FOUNDATION.md).

## Steady-state placement

DOC002-G9 completed the staged role-first migration and final execution-time
rescan. Direct durable records under `docs/project/` are no longer a supported
placement pattern; this `README.md` is the directory entry point and durable
records belong under one of the role directories above.

New unambiguous records use their canonical role immediately. If an unexpected
legacy/unclassified path is discovered later, do not duplicate or opportunistically
move it while performing unrelated work: classify it against the ratified path
contract and migrate it in an explicit bounded change with reference/link review.

The historical DOC002-A inventory and the DOC002 migration records preserve the
repository state they documented. Their old path spellings are evidence, not
current placement instructions.

# Implementation Status

<!-- BEGIN CANONICAL IMPLEMENTATION STATUS -->

This file is the canonical repository-level record of Protos implementation
progress. It is project state, not normative language specification.

Agents must verify this ledger against the current `origin/main` before acting
on it. Git history, the current implementation, tests, and normative dependency
owners remain the evidence used to verify a row.

Initialized: 2026-09-05
Repository implementation version at initialization: `0.2.83-SNAPSHOT`

## Status vocabulary

- `OPEN` — known implementation item not yet ready or not yet started.
- `READY` — dependencies are satisfied and implementation may begin.
- `IN_PROGRESS` — work is actively underway; advisory only, never a lock.
- `BLOCKED` — a recorded blocker prevents progress; see the blocker ledger.
- `BLOCKED_BY_DEPENDENCIES` — prerequisite implementation items remain open.
- `CLOSED` — required implementation, validation, and publication are complete.

`CLOSED` requires successful required tests and publication to `main`. Patch
generation, static validation, or `READY_FOR_USER_VALIDATION` alone do not close
an item.

## Core implementation

| Item | Description | Status | Closure evidence | Dependencies / notes |
|---|---|---|---|---|
| I001 | args as Array | CLOSED | historical; not backfilled | — |
| I002 | uniform represented-value lookup | CLOSED | historical; not backfilled | — |
| I003 | Standard String | CLOSED | historical; not backfilled | — |
| I004 | Array completion | CLOSED | historical; not backfilled | — |
| I005 | Standard Map | CLOSED | historical; not backfilled | — |
| I006 | IdentityMap / identity hashing | CLOSED | historical; not backfilled | — |
| I007 | Core Error infrastructure | CLOSED | historical; not backfilled | — |
| I008 | Modules | CLOSED | historical; see git history / CHANGELOG | — |
| I009 | Future / Task | CLOSED | historical; see git history / CHANGELOG | I009A + I009B complete |
| I010 | Parallel Execution | CLOSED | `f94362d50f9c809e62d2a84665f75b091bead2ca` | I009 |
| I011 | Actors complete | IN_PROGRESS | — | build on I009/I010; verify latest published slice on current main |
| I012 | Standard Bytes | CLOSED | historical; see git history / CHANGELOG | — |
| I013 | Standard Path | CLOSED | historical; see git history / CHANGELOG | — |
| I014 | Standard Byte I/O | CLOSED | `2462ba74298e94181489e13de4e25dbbb82b21f9` | I009 + I012 |
| I015 | Encoding / Text I/O | READY | — | I014 closed |
| I016 | Filesystem / File | READY | — | I013 + I014 closed |
| I017 | Process I/O / bootstrap | BLOCKED_BY_DEPENDENCIES | — | requires I016; re-audit exact current dependencies before starting |

## CLI implementation

| Item | Description | Status | Closure evidence | Notes |
|---|---|---|---|---|
| CLI001 | Basic CLI + persistent REPL | CLOSED | historical; not backfilled | — |
| CLI002 | Interactive terminal UX | CLOSED | historical; not backfilled | — |
| CLI003 | Multiline REPL input | OPEN | — | known REPL multiline/paste defect; mark CLOSED only after tests + publication |

## Update protocol

When publishing an implementation item:

1. verify this file against the current `origin/main`;
2. update the item's status in the same patch whenever practical;
3. record the implementation version if the item changes it;
4. record closure evidence:
   - use the concrete implementation SHA when it is already known, or
   - use `SAME_COMMIT` when the implementation and ledger update are the same
     commit;
5. update dependency transitions made possible by the closure;
6. keep unrelated rows unchanged;
7. do not use this ledger as a substitute for normative audit.

If an item is implemented through slices, the top-level item remains
`IN_PROGRESS` until every requirement assigned to that item is integrated,
validated, and published. Slice progress may be recorded in a dedicated
subsection when useful, but partial slice publication does not imply top-level
closure.

## Related project ledgers

- `docs/project/IMPLEMENTATION_BLOCKERS.md` — normative implementation blockers.
- `docs/project/OPEN_TASKS.md` — operational/project backlog.
- `docs/project/IMPLEMENTATION_STATUS.md` — implementation progress and
  dependency state.

<!-- END CANONICAL IMPLEMENTATION STATUS -->

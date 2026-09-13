# GITHUB016 — Incremental Project reconciliation and bounded descendant propagation

Status: **CLOSED**

Owning live Issue: GitHub #485.

Validation class: `PROJECT_COORDINATION_TOOLING_ONLY`.

## Pressure

The successful full audit run `34751228184` on 2026-09-13 took roughly 2m35s and
demonstrated the scaling shape of repository-wide reconciliation:

- 49 open Issues inspected by intake;
- 130 closed formal Issues structurally inspected;
- 274 unrecoverable legacy closed formal Issues skipped;
- 215 closed tracked Project items reconciled; and
- 49 open Project Issues reconciled.

That cost is acceptable for an explicit integrity audit, but it must not become
the cost of a routine scheduling-label event.

## Selected implementation boundary

Routine reconciliation is bounded by affected native hierarchy:

```text
ordinary Issue event
    -> changed Issue only

priority:* add/remove
    -> changed Issue
    -> native descendant subtree only

manual workflow_dispatch
    -> full repository audit
```

The GitHub native Parent/Sub-issue graph is the only descendant authority.
Traversal uses the REST sub-issues endpoint, is deterministic, handles arbitrary
depth, rejects repeated/cyclic nodes, and never infers hierarchy from names or
body prose.

Issue-scoped operation also resolves Project membership from the Issue's GraphQL
`projectItems` connection instead of enumerating the entire Project. Full audit
retains the existing Project-wide item enumeration because global integrity is
its explicit purpose.

## Priority semantics

GITHUB005 remains authoritative:

- explicit child `priority:*` overrides inheritance;
- otherwise the nearest open native ancestor with explicit priority wins;
- closed ancestors are skipped;
- inherited labels are never copied mechanically to children.

A bounded parent-priority removal has one extra operational distinction from
legacy Project-only migration. If a descendant has no remaining
explicit/inherited source, its stale derived Project Priority is cleared. It MUST
NOT be materialized as a new explicit child priority merely because the previous
inherited projection is still visible in the Project.

## Observability

Issue-scoped runs report bounded scope, including:

```text
RECONCILE_ROOT=#N
ISSUES_RECONCILED=1
UNRELATED_ISSUES_RECONCILED=0
FULL_RECONCILIATION=NO
```

Priority descendant propagation additionally reports:

```text
DESCENDANTS_DISCOVERED=N
OPEN_DESCENDANTS_RECONCILED=N
CLOSED_DESCENDANTS_TRAVERSED=N
```

Manual full audit reports `FULL_RECONCILIATION=YES`.

## Parent-change events

A native-parent move can affect the moved Issue and its descendants in exactly
the same way as a priority-source change. The helper's bounded subtree mode is
therefore reusable for that path. Workflow wiring is deferred until GitHub
Actions exposes a reliable repository event for native Parent/Sub-issue changes;
title/body inference is forbidden.

## Validation

The network-free self-test covers:

- deterministic multi-level branching traversal;
- no traversal of unrelated Issues;
- repeated/cyclic native hierarchy fail-closed behavior;
- Issue-local Project item selection and duplicate-membership rejection;
- disabled legacy Project-priority migration during descendant propagation; and
- clearing a stale inherited projection when no effective priority remains.

The live acceptance step is a real `priority:*` event on a parent with native
descendants, verifying that the workflow logs bounded descendant counters and
does not invoke `--reconcile-all`.

## Live acceptance evidence

Acceptance was exercised against `PERF004` / GitHub #52 on
`356a756dabe8075454d39474589fe2a837d4b460`. #52 had three native open
descendants: #107, #108 and #109.

The first controlled transition removed `priority:p2` from #52. Project status
sync run `34752063915` / run number `1257` completed successfully and reported:

```text
RECONCILE_ROOT=#52
ISSUES_RECONCILED=1
UNRELATED_ISSUES_RECONCILED=0
FULL_RECONCILIATION=NO

DESCENDANTS_DISCOVERED=3
OPEN_DESCENDANTS_RECONCILED=3
CLOSED_DESCENDANTS_TRAVERSED=0
UNRELATED_ISSUES_RECONCILED=0
FULL_RECONCILIATION=NO
```

The stale inherited P2 projection was cleared on #107, #108 and #109 rather than
being migrated into explicit child labels.

The second controlled transition restored `priority:p2` on #52. Project status
sync run `34752090060` / run number `1258` completed successfully and again
reported the same bounded scope. #107, #108 and #109 were projected back to P2
as inherited from #52.

In both event runs the manual full-audit steps were skipped. PERF004/#52 was
restored to its original `status:ready` + `priority:p2` state.

Live evidence is also recorded on owning Issue #485 in comment
`5652731194`.

## Closure

GITHUB016 is closed. Routine priority-label events now scale with the affected
native hierarchy rather than total repository history. The repository-wide
`workflow_dispatch` reconciliation remains available as an explicit audit/repair
operation.

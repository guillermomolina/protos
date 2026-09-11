# GITHUB009 — Native Issue dependency governance and reconciliation

Status: **CLOSED / ACTIVE**

Owning live Issue: GitHub #321.

This record governs GitHub Issue dependency coordination only. It does not define
Protos language semantics, specification authority, implementation behavior,
release contents, or scheduling priority.

## Decision

The project owner explicitly approved Candidate C-prime on 2026-09-10.

Protos keeps four GitHub coordination dimensions distinct:

| GitHub mechanism | Responsibility |
| --- | --- |
| Native Parent/Sub-issue | formal work hierarchy |
| Native `blocked by` / `blocking` | specific Issue-to-Issue dependency graph |
| `status:*` | live lifecycle state |
| `priority:*` | explicit scheduling priority |

No one of these dimensions is inferred mechanically from another.

## Native dependency authority

When one Issue is genuinely prevented from progressing by another specific Issue,
the native GitHub `blocked by` / `blocking` relationship is the canonical live
representation of that dependency.

Text in an Issue body or comment may explain why a dependency exists, preserve
migration history, or record how it was discovered, but it is not a substitute
for the native relationship once the exact blocker is known and the available
interface can establish it.

Agents creating or reconciling formal work SHOULD establish the native dependency
in the same coordination step when an exact current blocker is already known.
If the current environment cannot mutate native dependencies, report the pending
coordination operation rather than claiming that prose alone reconciled the graph.

## What is not a dependency

Do not infer a native dependency merely because two Issues have any of the
following relationships or text:

- parent / child or sibling hierarchy;
- the same `family:*` label;
- `status:blocked`;
- `Triggered by`;
- an ordinary prerequisite that has already been satisfied;
- a sequencing recommendation or preferred implementation order;
- historical `State at creation: BLOCKED_BY_DEPENDENCIES ...` prose;
- proximity in a roadmap, Project view, changelog, or work-item numbering; or
- a design/implementation relationship that does not currently prevent progress.

A native dependency represents a real Issue-to-Issue blocking relation, not a
general relation graph.

## Relationship to `status:blocked`

`status:blocked` answers **whether the Issue is currently blocked as a lifecycle
state**. A native dependency edge answers **which specific Issue participates in
the dependency graph**. Those are intentionally different questions.

An Issue may legitimately be `status:blocked` without a native dependency when
the blocking condition is external, not represented by an Issue, or not yet
resolved to one exact blocker.

Likewise, an Issue may retain native dependency history whose blockers are
already closed while the Issue itself is `Ready`, `In progress`, `Needs decision`,
`Paused`, `Review`, or another valid state.

Closing the final open blocker does not mechanically select the dependent Issue's
next `status:*` value. Coordination must determine whether the dependent becomes
Ready, resumes In progress, remains blocked for another reason, needs a decision,
or follows another valid lifecycle transition.

## Closed blockers and history

Native dependency relationships may remain after the blocker closes. GitHub's
active dependency counts distinguish unresolved blockers from the complete
historical relationship set.

Do not delete a dependency edge merely because its blocker closed. Retaining the
edge preserves useful project history without keeping the dependent Issue actively
blocked. Remove an edge only when the relationship itself was wrong, superseded,
or intentionally reconciled away.

## Hierarchy boundary

Dependencies do not propagate through Parent/Sub-issue hierarchy.

In particular:

- a child is not blocked by its parent merely because the parent contains it;
- a parent is not automatically blocked by every blocker of every child;
- sibling ordering does not imply a dependency;
- a child's blocker is not copied to the parent unless the parent itself is
  directly prevented by that same Issue for an independently valid reason; and
- milestone membership, Status and Priority likewise do not create dependency
  edges.

This avoids duplicate dependency graphs and keeps hierarchy and blocking
semantics independently useful.

## Multiple blockers

When an Issue is genuinely blocked by several exact Issues, retain one native
relationship for each blocker. Do not collapse several real blockers into a
synthetic umbrella edge merely to simplify display.

Conversely, do not attach every upstream transitive prerequisite. Prefer direct
blocking relationships; GitHub can expose the graph transitively through the
linked Issues without duplicating all ancestors on every dependent node.

## Reconciliation evidence

The GITHUB009 audit found several useful existing native patterns:

- TOOL001-F2E5 / #93 is natively blocked by TOOL001-F2E4 / #92;
- PERF004-B / #108 is natively blocked by PERF004-A / #107, and PERF004-C / #109
  is blocked by #108;
- TOOL002-I / #96 is blocked by TOOL002-H / #95, and TOOL002-J / #97 is blocked
  by #96;
- DOC001-N / #99 is blocked by DOC001-M / #98;
- PERF001-G / #106 retains its dependency on already-closed PERF001-F / #105;
  GitHub reports zero active blockers while retaining one total historical
  blocker, demonstrating that closed blocker history need not be deleted; and
- PERF006 / #262 can be `status:blocked` with no native blocker, demonstrating
  that lifecycle status and dependency identity are orthogonal.

The audit also found parent/child areas where copying child dependencies to an
umbrella parent would duplicate information. GITHUB009 therefore forbids
mechanical hierarchy propagation rather than attempting to normalize such cases
by rule.

## Migration classification

Current and historical relationships are reconciled in three classes:

1. **safe exact native additions** — the current blocker is unambiguous and
   supported by live/durable evidence;
2. **already native** — retain and verify the existing relationship; and
3. **ambiguous, stale or merely historical prose** — do not mutate the graph
   until exact current authority is recovered.

Historical text is not bulk-rewritten merely for cosmetic consistency.

## Automation boundary

Automation may inspect native dependency state, compare it with explicit
structured coordination facts, and report drift. It MUST NOT infer blocker
identity from arbitrary Issue prose, family, hierarchy, title similarity,
`status:blocked`, Project grouping, `Triggered by`, or implementation numbering.

Dependency automation must not automatically close dependent Issues or choose
their next lifecycle Status when blockers close.

## Publication and activation boundary

This governance publication ratifies the authority model but deliberately does
not mutate live Issue dependencies from the publication launcher. Patch
launchers remain GitHub-credential-independent and repository-only.

After publication, GITHUB009 performs a separate reviewed live reconciliation of
current Issue dependencies. The owning Issue remains open until that graph has
been reconciled and verified conflict-free. Closure evidence may then record the
live migration outcome without changing Protos semantics or runtime behavior.

## Live reconciliation result

The first post-ratification live reconciliation completed on 2026-09-10 against
all currently open GitHub Issues.

The active native blocking graph contained ten exact Issue-to-Issue edges:

```text
#47  -> #49
#48  -> #49
#92  -> #93
#95  -> #96
#96  -> #97
#47  -> #98
#48  -> #98
#98  -> #99
#107 -> #108
#108 -> #109
```

All ten active edges were already represented by GitHub native dependencies.
Therefore the reviewed migration required no live graph mutation:

```text
OPEN_DEPENDENCY_GRAPH_RECONCILED=YES
ACTIVE_EDGES=10
ACTIVE_EDGES_ALREADY_NATIVE=10
SAFE_NATIVE_ADDITIONS=0
NATIVE_REMOVALS=0
DEPENDENCY_CONFLICTS=0
AMBIGUOUS_TEXT_MUTATIONS=0
```

Two open Issues retain one native dependency each on an already-closed
predecessor:

```text
#91 -> #92
#94 -> #95
```

GitHub reports those relationships in `total_blocked_by` while reporting zero
active `blocked_by` entries for #92 and #95. They are retained intentionally as
historical dependency evidence under the ratified closed-blocker rule.

The reconciliation also confirmed deliberate no-edge cases:

- PERF006 / #262 remains `status:blocked` without a native dependency because no
  exact live Issue blocker is identified;
- WEB001-I / #300 records completed WEB001-H / #299 as a satisfied prerequisite,
  not a live blocker;
- PERF006-B / #276 records closed/ratified prerequisites without manufacturing
  native blocking edges;
- TOOL003 / #322 records `Triggered by` decision provenance without converting
  those decisions into blockers; and
- Parent/Sub-issue relationships were not propagated into dependency edges.

This result demonstrates that the pre-existing native graph already matched the
approved C-prime authority model. Reconciliation was therefore verification, not
an excuse to manufacture changes for migration symmetry.

## Closure

GITHUB009 is closed as a governance/reconciliation work item because:

- Candidate C-prime was explicitly approved by the project owner;
- the authority model was published on `main` in
  `d98296aaf6a1d84278863096486df3f4df1287ae`;
- every open Issue was audited for native dependency state;
- all ten exact active blocking edges were already native;
- the two retained closed-blocker relationships behave as intended;
- no missing safe exact edge, incorrect native edge, or dependency conflict was
  found; and
- no dependency was invented from Status, hierarchy, `Triggered by`, a satisfied
  prerequisite, or historical prose.

The GITHUB009 policy remains active steady-state governance after Issue #321 is
closed. Future agents must establish exact native dependency relationships when
known, preserve closed-blocker history, and fail closed rather than infer
dependency identity from non-authoritative metadata.

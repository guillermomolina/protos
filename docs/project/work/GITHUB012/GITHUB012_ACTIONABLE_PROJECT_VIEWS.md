# GITHUB012 — Actionable Project views and canonical status reconciliation

Status: **IN PROGRESS**

Owning live Issue: GitHub #470.

This record governs live GitHub coordination only. It changes no Protos language,
Tool, Standard Library, runtime or specification semantics.

## Canonical lifecycle state

Open Issues have exactly one canonical `status:*` label:

- `status:inbox`
- `status:ready`
- `status:in-progress`
- `status:needs-decision`
- `status:blocked`
- `status:paused`
- `status:review`

A different `status:*` spelling is invalid drift. In particular,
`status:needs-user-decision` is not a second lifecycle state. The Project sync
fails closed rather than ignoring an unknown status and silently projecting
Inbox.

Closed tracked Issues remain Project `Done`; `Done` is history, not an open-Issue
label.

## Actionable view contract

`Protos Development` retains all useful history, but the main **Work queue** is
not a history view. Its semantic membership is exactly open work whose Project
Status is one of:

- `In progress`
- `Review`
- `Ready`

Within that actionable set, Priority orders work P0, P1, P2, P3, then unset.
`Done`, `Inbox`, `Blocked`, `Paused`, and `Needs decision` do not compete in the
main Work queue. The saved Work queue view MUST additionally filter to
**top-level Issues only** (`no:parent-issue`): native sub-issues remain visible
inside their parent hierarchy and dedicated/detail views, but they do not compete
as independent top-level workstreams.

### Work queue presentation contract

The saved **Work queue** SHOULD use the table layout and group horizontally by
`Priority`. The intended visual order is:

```text
P0      immediate / exceptional attention
P1      next / high-priority work
P2      normal planned work
P3      opportunistic / later work and low-attention reminders
unset   not yet explicitly scheduled
```

This grouping is presentational, not a second scheduling authority: GITHUB005
still owns the meaning and projection of Priority. The important usability
property is that P0/P1 remain visually dominant while P3 stays visible near the
bottom instead of disappearing from the maintainer's routine view.

The Work queue MUST NOT exclude an Issue merely because it is suitable for
community contribution. Community suitability is orthogonal to hierarchy and
Priority:

- a community-facing task with a genuine semantic parent SHOULD be a native
  sub-issue and therefore does not independently compete in the top-level Work
  queue;
- a standalone community-facing task with no genuine semantic parent MAY remain
  top-level;
- community suitability alone does not manufacture `priority:p3`, but when the
  project owner deliberately schedules such standalone work as opportunistic /
  later work, P3 is the normal reminder tier; and
- a community-facing task may legitimately be P0/P1/P2 when roadmap urgency
  independently justifies that priority.

The dedicated **Community** view remains the detailed contribution surface. Work
queue provides only reminder-level visibility through ordinary Priority ordering;
do not create a synthetic `COMMUNITYxxx` parent merely to hide contribution tasks.

For maintainer readability, the Work queue SHOULD keep these fields visible when
available:

- `Status`;
- `Assignees`;
- `Labels`;
- `Linked pull requests`; and
- `Sub-issue progress`.

`Linked pull requests` is especially important for external contribution work.
GitHub may reject an external PR author as an Issue assignee when that account is
not assignable in the repository; in that case an owner fallback assignee
represents repository coordination responsibility, not authorship or execution
of the linked contribution.

The maintained view model is:

1. **Work queue** — top-level In progress / Review / Ready.
2. **Decisions** — open Issues whose Status is Needs decision. Assignment is
   irrelevant to membership: needing project-owner input and being assigned to
   `guillermomolina` are different concepts.
3. **Upstream** — open Issues carrying `family:UPSTREAM`, regardless of lifecycle
   Status. This is an inspection/coordination view, not an actionable queue. An
   UPSTREAM item may also appear in Work queue when its lifecycle is Ready,
   In progress, or Review.
4. **Blocked** — Blocked / Paused.
5. **Triage** — Inbox.
6. **History / Done** — Done.

The legacy saved views named **Core** and **Needs Guillermo** have no maintained
semantic role:

- **Core** is repurposed/renamed to **Upstream** rather than retained as an
  ambiguous project slice.
- **Needs Guillermo** is repurposed/renamed to **Decisions**. Do not use
  assignee identity as a proxy for `Needs decision`.

Representative GitHub Projects filter intent is:

```text
Work queue:
  is:issue is:open no:parent-issue
  Status in {In progress, Review, Ready}

Decisions:
  is:issue is:open
  Status = Needs decision

Upstream:
  is:issue is:open
  label = family:UPSTREAM

Blocked:
  is:issue is:open
  Status in {Blocked, Paused}

Triage:
  is:issue is:open
  Status = Inbox

History / Done:
  Status = Done / closed history
```

Exact saved-filter spelling remains GitHub UI configuration; the semantic
membership above is the durable contract.

## Active ownership invariant

`status:in-progress` means somebody is actively responsible for driving the
Issue. Every open Issue in that state MUST therefore have at least one assignee.
Repository synchronization enforces the invariant as follows:

- preserve every existing assignee; never replace a human contributor merely
  because automation or an agent is assisting;
- if an Issue reaches `status:in-progress` with no assignee, assign
  `guillermomolina` as the repository-owner fallback;
- do not auto-assign `Ready`/`Inbox` work merely because it is available; and
- do not infer assignment on `Blocked`, `Needs decision`, `Paused` or `Review`
  solely from the status name. Existing responsibility may remain.

This applies equally to a top-level workstream that is itself `In progress`. A
parent does not become assigned merely because a child is active, but when the
parent's own canonical status is `In progress`, the parent itself has active
coordination responsibility and must satisfy the invariant.

Priority remains orthogonal to lifecycle and ownership. GITHUB005 defines
explicit priority plus native-parent inheritance; no lifecycle transition
automatically means P0/P1/P2/P3.

Saved Project-view filters are GitHub UI configuration, not repository semantic
authority. Current repository automation owns Project membership, Status and
Priority projection; it does not currently mutate saved view definitions. The
view configuration must therefore be verified explicitly in the Project UI after
this repository hardening is published.

## 2026-09-13 live reconciliation

The audit that opened GITHUB012 found a split lifecycle vocabulary and stale
Inbox projections. Live coordination was corrected where current evidence was
unambiguous:

- TEST001 / #449 -> `status:in-progress`;
- TOOL005 / #468 -> `status:blocked`, awaiting D122;
- D122 / #469 -> canonical `status:needs-decision`;
- D110 / #435 -> `status:in-progress` after recorded owner approval of B-prime;
- D121 / #456 -> `status:in-progress` after recorded owner approval of A-prime;
- the accidental `status:needs-user-decision` label was removed from all open
  Issues encountered by this reconciliation;
- AUD004 / #450 -> Ready; AUD005 / #451 and AUD006 / #453 -> In progress.

Historical prose is not live status authority. Other Inbox items must be triaged
from current comments/dependencies/published evidence rather than keyword scans
of old bodies.

## 2026-09-13 saved-view reconciliation reopening

GITHUB012 was reopened after live inspection showed that the saved Project views
had not actually reached the durable contract before the earlier closure:

- the existing `Core` view was not a useful maintained coordination surface;
- the existing `Needs Guillermo` view conflated assignee identity with the
  canonical `Needs decision` lifecycle state; and
- the newly formalized UPSTREAM family needed a dedicated inspection view so
  paused external relationships remain visible without polluting Work queue.

The selected live reconciliation is therefore:

```text
Core             -> Upstream
Needs Guillermo  -> Decisions
```

`Upstream` must include open `family:UPSTREAM` items across lifecycle states.
UPSTREAM001 / #480 is the first acceptance case: while it remains
`status:paused`, it must be absent from Work queue but present in Upstream.

## Closure gate

GITHUB012 can close after the hardened synchronizer is published and validated,
a full Project reconciliation succeeds, and the saved Project views are verified
to match the view contract above. In particular, closure now requires live
verification that:

- `Work queue` contains only top-level In progress / Review / Ready work;
- `Work queue` is presented as a Priority-grouped table with P0/P1 ahead of P2,
  P3 retained as low-attention reminder work, and the agreed coordination
  fields visible;
- standalone community-facing P3 work remains visible in Work queue while
  semantically parented community work is represented through native hierarchy;
- `Core` has been replaced by `Upstream`;
- `Needs Guillermo` has been replaced by `Decisions`;
- UPSTREAM001 / #480 appears in `Upstream` while remaining absent from
  `Work queue` in its paused state; and
- Blocked, Triage and History / Done retain their intended membership.

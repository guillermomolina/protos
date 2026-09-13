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
main Work queue.

The maintained view model is:

1. **Work queue** — In progress / Review / Ready.
2. **Decisions** — Needs decision.
3. **Blocked** — Blocked / Paused.
4. **Triage** — Inbox.
5. **History / Done** — Done.

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

## Closure gate

GITHUB012 can close after the hardened synchronizer is published and validated,
a full Project reconciliation succeeds, and the saved Project views are verified
to match the actionable-view contract above.

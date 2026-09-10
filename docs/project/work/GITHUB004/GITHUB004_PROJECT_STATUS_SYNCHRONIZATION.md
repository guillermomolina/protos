# GITHUB004 — Automatic Project status synchronization

Status: **IN_PROGRESS**

Owning live Issue: GitHub #307.

This record defines project-coordination machinery only. It does not define
Protos language semantics, approve a `Dxxx`/`PLATxxx` decision, or replace
durable repository evidence.

## Authority model

The live-state chain is intentionally one-way:

```text
GitHub Issue state + status:* label
            |
            v
Protos Development / Status
```

The Issue is the canonical live work-state source. `Protos Development` is the
derived dashboard/scheduling projection. Project metadata cannot ratify design,
override the repository, clear a durable blocker, or act as a publication lock.

Every open Issue tracked by the dashboard carries exactly one of:

- `status:inbox`
- `status:ready`
- `status:in-progress`
- `status:needs-decision`
- `status:blocked`
- `status:paused`
- `status:review`

Closed Issues project to `Done` regardless of the retained open-state label.

`status:paused` is deliberately distinct from `status:blocked`: the former means
work is intentionally suspended/paused (for example by the project owner); the
latter means an unresolved dependency, decision, defect, environment condition,
or other blocker prevents progress.

The Project Status vocabulary is:

```text
Inbox
Ready
In progress
Needs decision
Blocked
Paused
Review
Done
```

`family:<FAMILY>` remains the sole formal-family classifier. Do not create or
maintain a Project `Family` duplicate.

`Priority`, `Area`, and `Roadmap` remain optional planning metadata. GITHUB004
does not infer them from status labels and does not make them semantic or
repository authority.

## Automatic convergence

`.github/workflows/project-status-sync.yml` responds to Issue lifecycle and label
events and delegates logic to `scripts/project_status_sync.py`.

For an open Issue:

1. if a newly-added `status:*` label exists, it wins and any older `status:*`
   labels are removed;
2. otherwise exactly one existing `status:*` label is required;
3. a status-less Issue receives `status:inbox`;
4. the Issue is added to `Protos Development` if missing; and
5. Project `Status` is set from the canonical label.

For a closed Issue already in the Project, Project `Status` becomes `Done`.
Closed historical Issues that were never tracked are not added solely because
they closed. Reopening reuses the retained open-state label, falling back to
Inbox only when none exists.

Manual `workflow_dispatch` performs a one-time/full reconciliation in both
directions needed to repair legacy drift: every closed repository Issue already
present in the Project is forced to `Done`, and every current open repository
Issue is added if missing and projected from its canonical `status:*` label. It
is idempotent and can be rerun whenever Project drift is suspected.

## Token and security boundary

Repository `GITHUB_TOKEN` owns only repository-local Issue label mutation.
The personal Project is user-owned, so Project GraphQL access uses the repository
Actions secret:

```text
PROTOS_PROJECT_TOKEN
```

The secret value is never stored in the repository. Use a GitHub classic personal
access token scoped only as required for Projects (`project`); choose a sensible
expiration and rotate it before expiry.

The helper dynamically resolves the user Project, `Status` field, Status options,
and item IDs. Opaque GraphQL node/option IDs are not committed.

A Project-sync failure is a visible coordination failure but does not alter
published Protos truth and does not invalidate an otherwise valid code
publication.

## One-time activation

Before the first full reconciliation:

1. In `Protos Development` Project #1, add the Status option `Paused` alongside
   the existing Inbox / Ready / In progress / Needs decision / Blocked / Review /
   Done options.
2. Create a GitHub classic PAT with the `project` scope.
3. In `guillermomolina/protos` → Settings → Secrets and variables → Actions,
   create repository secret `PROTOS_PROJECT_TOKEN` with that PAT.
4. Publish this GITHUB004 repository change.
5. In Actions, run `Project status sync` manually once. The dispatch repairs
   stale closed cards to `Done`, reconciles every current open Issue, and adds
   missing open items.
6. Review the Inbox column: legacy open Issues whose current fine-grained state
   was not safely machine-readable land there rather than being guessed from
   stale Issue-body prose.

GitHub's built-in Project workflows for auto-add and closed-to-Done may remain
enabled as a first-line convenience. The repository workflow is the convergence
mechanism for the complete Protos `status:*` vocabulary and missing-item repair.

## Agent rule

When an agent with Issue-write capability materially changes an open Issue's live
state, it must replace that Issue's `status:*` label coherently with the new
state. It does not need Project API capability and should not directly mutate the
Project field. The automatic workflow projects the Issue-owned state.

When Issue mutation is unavailable, report the missing Issue-state update
explicitly. Do not fabricate a Project update instead.

## Closure evidence required

GITHUB004 closes only after:

- this policy/machinery is published;
- `PROTOS_PROJECT_TOKEN` is configured;
- Project Status contains `Paused`;
- full reconciliation completes;
- representative Ready → In progress → Needs decision / Blocked / Paused /
  Review → Done projection is demonstrated;
- reopen projection is demonstrated; and
- the Project no longer relies on manual status-field maintenance during normal
  work.

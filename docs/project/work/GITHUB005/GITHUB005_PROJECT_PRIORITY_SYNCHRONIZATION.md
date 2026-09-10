# GITHUB005 — Automatic Project priority synchronization

Status: **IN_PROGRESS**

Owning live Issue: GitHub #309.

This record extends the GitHub-native coordination model only. It does not define
Protos language semantics, approve a `Dxxx`/`PLATxxx` decision, replace durable
repository evidence, or make scheduling priority a publication lock.

## Authority model

The live scheduling-priority chain is one-way:

```text
GitHub Issue priority:* label
            |
            v
Protos Development / Priority
```

The Issue label is canonical live priority. The Project field is a derived
dashboard projection.

GITHUB001 already established Project `Priority` values P0 / P1 / P2 / P3 and
left them unset unless work was explicitly prioritized. GITHUB005 preserves that
contract rather than inventing a default priority.

An Issue may carry at most one of:

- `priority:p0` — immediate/critical attention; exceptional;
- `priority:p1` — next/high-priority work;
- `priority:p2` — normal planned work; or
- `priority:p3` — opportunistic/later work.

No `priority:*` label means Project Priority is empty for an open Issue.

Priority is orthogonal to Status. `Needs decision` is not automatically P0,
`In progress` is not automatically P1, and `Ready` is not automatically P2.
Scheduling priority must reflect current coordination evidence rather than
duplicating lifecycle state.

## Automatic convergence

The existing `.github/workflows/project-status-sync.yml` remains the single
repository workflow that mutates `Protos Development`. Its helper
`scripts/project_status_sync.py` now owns both Status and Priority projection.

For an open Issue:

1. GITHUB004 chooses/reconciles exactly one `status:*` label;
2. zero or one `priority:*` label is valid;
3. a newly-added `priority:*` wins over an older priority label and the older
   label is removed;
4. multiple priority labels without a decisive newly-added label fail closed;
5. the Project item is created if missing;
6. Project Status is projected from `status:*`; and
7. Project Priority is projected from `priority:*`, or cleared when no priority
   label exists.

Full `workflow_dispatch` also creates the four repository `priority:*` labels if
missing and clears stale Project Priority values from unprioritized open Issues.

A closed Issue projects Status to `Done` and retains its last Issue priority label
as historical scheduling context. Closure does not rewrite Project Priority.
Reopen projects the retained priority again.

## Agent rule

Project-owner priority instructions override agent heuristics.

Agents with Issue-write capability may maintain an already-established priority
or change it when current scheduling evidence justifies the change. Do not churn
priority merely because implementation progressed, and do not infer priority from
Status alone.

Use the levels as:

- P0 — immediate/critical attention; exceptional;
- P1 — next/high-priority set;
- P2 — ordinary planned work;
- P3 — opportunistic/later work.

If current evidence does not justify a priority, leave the Issue without a
`priority:*` label. Do not use P2 as an automatic default.

Agents do not need direct Project API capability and should not mutate Project
Priority merely to mirror an Issue transition. Repository automation owns the
projection.

## Security and failure boundary

`GITHUB_TOKEN` owns repository-local label operations.
`PROTOS_PROJECT_TOKEN` owns user-Project GraphQL mutation.

Project priority remains advisory coordination metadata. A sync failure is
visible coordination failure but cannot approve/reject design, change Protos
semantics, clear a blocker, or invalidate an otherwise valid publication.

## Closure evidence required

GITHUB005 closes only after:

- this policy and automation are published;
- full reconciliation creates/verifies `priority:p0` through `priority:p3`;
- unprioritized open Issues have stale Project Priority cleared;
- representative priority add/replacement/removal projection is demonstrated;
- the current actionable Work queue has an explicitly reviewed initial
  prioritization; and
- routine work no longer requires manual Project Priority maintenance.

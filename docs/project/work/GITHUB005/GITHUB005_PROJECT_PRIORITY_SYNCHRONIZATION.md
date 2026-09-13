# GITHUB005 — Automatic Project priority synchronization

Status: **IN_PROGRESS**

Owning live Issue: GitHub #309.

This record extends the GitHub-native coordination model only. It does not define
Protos language semantics, approve a `Dxxx`/`PLATxxx` decision, replace durable
repository evidence, or make scheduling priority a publication lock.

## Authority model

The live scheduling-priority chain remains one-way, but native Issue
hierarchy can now supply an inherited effective priority:

```text
explicit priority:* on Issue ----------------------+
                                                   |
otherwise nearest open ancestor explicit priority:*+--> effective Priority
                                                   |
otherwise unset -----------------------------------+
                                                   |
                                                   v
                                      Protos Development / Priority
```

A `priority:*` label on an Issue is its explicit priority override. The Project
field is a derived dashboard projection of the Issue's **effective priority**.
Inherited priority is not copied onto the child as a `priority:*` label; this
preserves the distinction between inheritance and an intentional child override.

GITHUB001 already established Project `Priority` values P0 / P1 / P2 / P3 and
left them unset unless work was explicitly prioritized. GITHUB005 preserves that
contract rather than inventing a default priority.

An Issue may carry at most one of:

- `priority:p0` — immediate/critical attention; exceptional;
- `priority:p1` — next/high-priority work;
- `priority:p2` — normal planned work; or
- `priority:p3` — opportunistic/later work.

For a top-level open Issue, no `priority:*` label still means effective
Priority is unset. For a sub-issue, absence of an explicit `priority:*` means:

1. walk native Parent/Sub-issue ancestry toward the root;
2. use the nearest **open** ancestor carrying an explicit `priority:*`;
3. if no open ancestor supplies one, leave effective Priority unset.

An explicit child `priority:*` always overrides inherited priority. A closed
ancestor's retained historical priority is skipped for live inheritance.

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
2. zero or one explicit `priority:*` label is valid;
3. a newly-added `priority:*` wins over an older explicit priority label and the
   older label is removed;
4. multiple explicit priority labels without a decisive newly-added label fail
   closed;
5. when explicit priority is absent, native parent ancestry is resolved and the
   nearest open ancestor's explicit priority becomes the effective priority;
6. the Project item is created if missing;
7. Project Status is projected from `status:*`; and
8. Project Priority is projected from effective priority, or cleared when neither
   the Issue nor an open ancestor supplies one.

A `priority:*` label add/removal first reconciles the changed Issue using the
event as the decisive label transition, then performs a full open-Issue
reconciliation so descendants immediately converge on the new inherited value.
Full `workflow_dispatch` likewise recomputes inheritance for every open Issue and
creates the four repository `priority:*` labels if missing.

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

If current evidence does not justify an explicit priority on a top-level
Issue, leave it without a `priority:*` label. Do not use P2 as an automatic
default merely because an Issue becomes `Ready` or `In progress`.

For a sub-issue, leave `priority:*` absent when it should follow the workstream's
priority; repository automation will inherit from the nearest open ancestor. Add
an explicit child priority only when intentionally overriding the inherited
value. Do not duplicate the parent label onto children merely to make Project
Priority visible.

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

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

For a top-level open Issue, no `priority:*` label normally means effective
Priority is unset **after legacy/manual Project-only priority migration has been
resolved**. Existing Project `Priority` is protected migration evidence: when an
open Issue has neither an explicit nor inherited Issue priority but its existing
Project item still carries P0/P1/P2/P3, synchronization MUST preserve that value
and materialize the matching `priority:p*` label before Issue-owned priority
authority is considered complete.

For a sub-issue, absence of an explicit `priority:*` means:

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
8. Project Priority is projected from effective priority. If no Issue/ancestor
   priority exists but the Project item already carries P0/P1/P2/P3, that value
   is preserved and migrated into the corresponding Issue `priority:*` label;
   absence of Issue priority alone is never destructive.
9. Project Priority may be cleared automatically only from an explicit removal
   of the Issue's priority label when no inherited priority replaces it.

A `priority:*` label add/removal first reconciles the changed Issue using the
event as the decisive label transition, then walks only that Issue's native
descendant subtree and recomputes effective Priority there. Unrelated Issues are
not scanned or projected merely because one workstream changed scheduling.
Explicit child priority overrides remain authoritative, while descendants below
that override continue to inherit from their nearest open explicit ancestor.

During bounded descendant propagation, an inherited Project Priority that no
longer has an explicit/open-ancestor source is cleared rather than migrated into
a new child `priority:*` override. Legacy Project-only migration remains available
for ordinary Issue reconciliation and full audit, where the existing Project
value may still represent pre-GITHUB005 authority rather than stale inheritance.

Full `workflow_dispatch` remains intentionally repository-wide: it recomputes
inheritance for every open Issue, creates the four repository `priority:*` labels
if missing, and migrates any still-present legacy/manual Project-only
P0/P1/P2/P3 value into durable Issue priority before normal projection. It MUST
NOT erase Project-only priority merely because a label was absent.

This migration rule is deliberately one-way compatibility, not dual authority.
Once a `priority:*` label exists (explicitly or via migration), Issue/native-parent
state remains authoritative. A later manual Project-field edit does not silently
override an existing Issue priority label.

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
- legacy/manual Project-only priorities that still exist are adopted into
  durable `priority:*` labels rather than erased;
- any truly stale Project Priority is cleared only through an explicit reviewed
  priority-removal transition, not by absence-of-label inference;
- representative priority add/replacement/removal projection is demonstrated;
- the current actionable Work queue has an explicitly reviewed initial
  prioritization; and
- routine work no longer requires manual Project Priority maintenance.

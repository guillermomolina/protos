# GITHUB007 — Issue intake and creation governance

Status: **IN_PROGRESS — PENDING LIVE ACTIVATION**

Owning live Issue: GitHub #319.

This record governs GitHub Issue intake only. It does not define Protos
semantics, specification authority, implementation behavior, release contents,
or scheduling priority.

## Goals

GITHUB004–GITHUB006 established Issue-owned Status/Priority, automatic Project
projection, and native Issue hierarchy. GITHUB007 makes newly created Issues
enter those structures coherently instead of relying on periodic manual cleanup.

The selected model deliberately separates two automation responsibilities:

1. `.github/workflows/issue-intake.yml` owns intake-only formal classification
   and native-parent reconciliation.
2. `.github/workflows/project-status-sync.yml` remains the only automation that
   normalizes `status:*` / `priority:*` and projects them into
   `Protos Development`.

Neither workflow becomes language/design authority.

## Intake classes

### Tracked project work

The tracked-work form is maintainer-oriented and assumes a collision-safe,
already-allocated formal identifier.

Form-created tracked work starts in `status:inbox`, with scheduling Priority
unset. The Issue body records the formal work item and one parent Issue number or
`none`.

The intake helper derives `family:<FAMILY>` from the formal identifier prefix.
It never asks users to duplicate that classification in another field.

A formal child must have a native parent. When the native relation is absent,
one unambiguous textual/form parent declaration may be consumed only as bootstrap
input to create the GITHUB006-native relation. A conflicting existing native
parent fails closed and is never replaced automatically.

### Community intake

Bug reports, documentation problems, and community requests start in
`status:inbox` and carry no formal family merely because a form was used.

In particular, using the Bug report form does not allocate a `BUGxxx` identifier
or imply `family:BUG`. Formalization is a later maintainer action.

Community requests are for concrete adoption, example, tooling, packaging or
ecosystem improvements. Open-ended language/design exploration remains in
Discussions.

## Trust boundary

An arbitrary contributor must not be able to allocate Protos formal work simply
by typing an identifier-shaped title.

Automatic formal reconciliation therefore applies only when either:

- GitHub reports the Issue author as OWNER, MEMBER or COLLABORATOR; or
- a maintainer has already adopted the Issue by applying a `family:*` label.

An untrusted formal-looking Issue remains ordinary triage and receives no family
or hierarchy authority from the intake helper.

## Status and priority boundary

Issue forms use `status:inbox` as the neutral initial lifecycle state. Existing
GITHUB004 synchronization remains the fallback for Issues created outside forms.

No form assigns `priority:*`. GITHUB005's explicit-unset rule remains intact:
Priority is added only from real scheduling evidence, never from intake kind,
family or status.

The intake helper does not infer `Ready`, `Blocked`, `Needs decision`, assignee
or Priority from prose.

## Steady-state reconciliation

`scripts/issue_intake.py` supports:

- one-Issue reconciliation for creation/edit/reopen and formal-family label
  changes;
- full open-Issue reconciliation through manual `workflow_dispatch`; and
- a network-free self-test for identifier, trust, family, parent and conflict
  rules.

Full reconciliation processes every open Issue, repairs deterministic family or
missing-native-parent drift, records community/untrusted intake without
promoting it, and fails closed after the scan if any trusted formal Issue still
has an unresolved hierarchy conflict.

## Activation evidence

At authoring time, LM009-E / GitHub #318 is a real post-GITHUB006 drift case:
its body declares `Parent: #288`, while the GitHub native parent endpoint reports
no parent. The first full GITHUB007 reconciliation is expected to establish
#288 as its native parent and verify the relation.

## Closure

GITHUB007 closes only after:

- this policy, forms, helper and workflow are published;
- helper self-tests pass in GitHub Actions;
- a full live reconciliation completes without unresolved errors;
- #318 is repaired and verified as a native child of #288 (or equivalent
  post-GITHUB006 missing-parent evidence is reconciled if live state changes
  before activation); and
- Issue-form content is visible on `main` with neutral Inbox/no-priority intake.

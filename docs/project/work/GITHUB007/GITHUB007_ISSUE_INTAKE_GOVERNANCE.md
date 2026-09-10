# GITHUB007 — Issue intake and creation governance

Status: **CLOSED / ACTIVE**

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

Live activation completed successfully on 2026-09-10 through GitHub Actions
workflow run `34521196152`, executing the published GITHUB007 helper from commit
`8c8f98b13108ccbd73d2298159cd988f7910ff98`.

The workflow's network-free helper self-test passed, and the full live
reconciliation reported:

```text
OPEN_ISSUES_SCANNED=35
FORMAL_ISSUES_RECONCILED=24
COMMUNITY_ISSUES_OBSERVED=11
UNTRUSTED_FORMAL_CANDIDATES=0
FAMILY_RECONCILIATIONS=0
NATIVE_PARENTS_ADDED=1
UNRESOLVED_INTAKE_ERRORS=0
ISSUE_INTAKE_RECONCILIATION: PASS
```

The intended post-GITHUB006 drift case was repaired by the workflow itself:

```text
NATIVE_PARENT_ADDED: child=#318 parent=#288
```

A subsequent GitHub native-parent lookup confirmed that LM009-E / #318 resolves
to LM009 / #288. This proves the missing-parent convergence path against live
repository state rather than only the mock/self-test path.

## Closure

All GITHUB007 activation conditions are satisfied:

- policy, forms, helper and workflow are published on `main`;
- helper self-tests passed in GitHub Actions;
- the first full live reconciliation completed with zero unresolved intake
  errors;
- LM009-E / #318 was repaired and independently verified as a native child of
  LM009 / #288; and
- Issue-form content is published with neutral `status:inbox` intake and no
  automatic scheduling priority.

GITHUB007 is therefore closed as an implementation/governance work item. The
published intake workflow and helper remain active steady-state repository
coordination infrastructure.

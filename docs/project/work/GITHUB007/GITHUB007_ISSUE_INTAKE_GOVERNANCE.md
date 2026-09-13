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

Before reconciling a trusted formal Issue, the helper also scans authorized
formal Issue titles across both open and closed Issues. Exact identifier reuse
fails closed. The lower GitHub Issue number is the stable GitHub owner of an
accidental race unless a durable repository allocation or explicit owner
reconciliation says otherwise; a later colliding Issue must be reallocated
before durable publication. Formal child identifiers are compared exactly, so
`TEST001` and `TEST001-A` do not collide.

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

GITHUB015 hardens a different path: **trusted direct formal publication by an
agent/maintainer**. Such a publisher already has explicit project intent and must
provide the known lifecycle/ownership/scheduling facts itself. It may not rely on
the neutral `Inbox` fallback to repair an omitted formal status. The Project sync
therefore fails closed when a `family:*` Issue has no canonical status, while
ordinary community intake with no formal family may still default to Inbox.

For `status:in-progress` and `status:needs-decision`, direct formal publication
must also resolve assignment and effective Priority. Child Priority remains
inherited from the nearest open native ancestor; it is not copied as a child
label.

## Steady-state reconciliation

`scripts/issue_intake.py` supports:

- one-Issue reconciliation for creation/edit/reopen/close and formal-family
  label changes;
- full open-intake plus closed-formal structural reconciliation through manual
  `workflow_dispatch`; and
- a network-free self-test for identifier, trust, family, parent, collision,
  conflict, and closed-structure rules.

The identifier-collision preflight includes closed Issues because allocated
identifiers are never reusable merely because work closed. Family/native-parent
structure is likewise durable: trusted formal Issues remain structurally
reconcilable after closure. Closed reconciliation is deliberately structural
only; it does not reopen work or infer lifecycle, assignee, or scheduling
Priority.

Full reconciliation processes every open Issue plus the closed formal subset
for which deterministic structure can be checked without inventing history.
Open intake retains the ordinary community/untrusted behavior.

For work closed before GITHUB015 closed-structure enforcement became active at
`2026-09-13T10:02:02Z`, an explicit parent declaration remains valid bootstrap
evidence for a missing native relation. Legacy child-shaped Issues with neither a
native parent nor an explicit declaration are skipped as unrecoverable historical
structure rather than guessed or allowed to fail every future synchronization.
When a legacy closed Issue already has a native parent, that native relation wins
over contradictory stale parent prose. Every formal Issue closed at or after the
enforcement instant is checked strictly, including child-shaped Issues with no
parent declaration, so new publication defects still fail closed.

Under GITHUB015 the Project-status workflow executes this intake reconciliation
as a precondition in the **same job** before it projects status/priority. A full
manual Project reconciliation likewise runs full intake first. This ordering is
intentional: native hierarchy must exist before effective Priority inheritance is
computed, so two independent workflows cannot race and leave a newly created
formal child with stale/unset Project Priority. The standalone Issue-intake
workflow remains an independent convergence safety net.

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

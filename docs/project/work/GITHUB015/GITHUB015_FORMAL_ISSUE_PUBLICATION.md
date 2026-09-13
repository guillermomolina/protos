# GITHUB015 — Formal Issue publication transaction and postcondition enforcement

Status: **IN PROGRESS**

Owning live Issue: GitHub #484.

## Problem

GITHUB007 established deterministic formal-family/native-parent intake and
GITHUB004/GITHUB005/GITHUB012 established canonical Project lifecycle, ownership,
priority inheritance and actionable views. Live fresh-agent tests on 2026-09-13
showed that those individually-correct mechanisms did not yet form one atomic
publication contract.

Observed regressions included:

- LIB012-0 / #481 being described as a subissue while only textual parent prose
  existed and first-class coordination metadata was initially incomplete;
- PERF006-C / #482 being created for P0 PERF006 with textual parent #262 but no
  native parent, preventing reliable inherited Priority projection;
- PLAT033 / #483 being opened as a decision gate while its live lifecycle state
  initially remained neutral Inbox instead of Needs decision; and
- a subsequent PLAT033 path recording an owner approval/ratification even though
  the triggering coordination interaction did not itself contain an explicit
  selection of Candidate A-prime. That approval provenance must be resolved by
  the owner; GITHUB015 does not silently rewrite the decision while evidence is
  being audited.

The defect is therefore not one missing label. Formal publication lacked a
transaction boundary and a fail-closed definition of completion.

## Selected contract

Direct trusted formal publication is complete only after live GitHub state has
been re-read and all applicable postconditions pass:

```text
FORMAL_IDENTIFIER_UNIQUE=PASS
FAMILY=PASS
CANONICAL_STATUS=PASS
ASSIGNEE_INVARIANT=PASS
NATIVE_PARENT=PASS|NOT_APPLICABLE
EFFECTIVE_PRIORITY=RESOLVED|INTENTIONALLY_UNSET
PROJECT_ROUTING=PASS
DECISION_APPROVAL_PROVENANCE=PASS|NOT_APPLICABLE
REQUIRED_DURABLE_PUBLICATION=PASS|NOT_APPLICABLE
```

`Parent: #N`, `Parent work item: #N`, dependency prose and naming conventions do
not satisfy the native-parent postcondition.

## Neutral intake versus trusted publication

Community/form intake remains deliberately neutral. The intake helper does not
guess Ready/Blocked/Needs-decision, assignee or Priority from arbitrary prose.

Trusted direct publication is different: the agent/maintainer is already acting
on explicit project intent. It must publish the known facts rather than omit them
and expect neutral intake to infer them. If intent is genuinely ambiguous, keep
the item in explicit Inbox/triage rather than guessing.

A formal Issue with `family:*` and no canonical status is therefore a publication
error. Ordinary non-formal community Issues may continue to receive the Inbox
default.

## Ownership and scheduling invariants

`status:in-progress` and `status:needs-decision` both require an assignee. Existing
assignees are preserved; if the set is empty, `guillermomolina` is the
coordination fallback.

Those statuses also require effective Priority:

- top-level: explicit `priority:p0` / `p1` / `p2` / `p3`;
- formal child: own explicit override, otherwise nearest open native ancestor;
- inherited Priority is Project projection only and MUST NOT be copied as a child
  label.

This does not make Ready imply P2, In progress imply P1, or Needs decision imply
P0. The actual schedule remains explicit.

## Ordering guarantee

The Project-status workflow must reconcile GITHUB007 intake/native hierarchy in
the same job **before** status/Priority projection. Manual full reconciliation
must do the same. This removes the race where Project sync observes a child
before its native parent exists and therefore cannot inherit Priority.

The independent Issue-intake workflow remains active as a convergence safety
net; it is no longer the only opportunity for hierarchy convergence before
Project projection.

## Closure preserves formal structure

Closure changes lifecycle, not identity or hierarchy. Formal family and native
Parent/Sub-issue relationships remain durable historical facts after an Issue is
closed.

The intake helper therefore reconciles trusted closed formal Issues structurally:
family remains deterministic from the formal identifier, and a formal child (or
an Issue carrying an explicit parent declaration) must still have the matching
native parent. This structural repair MUST NOT reopen work, assign responsibility,
change status, manufacture Priority, or copy an inherited parent Priority onto a
closed child.

This closes the #481 regression class: a phase may complete quickly enough to
close before asynchronous intake runs, but closure must not allow a text-only
parent declaration to become permanent historical drift.

### Legacy closed-structure boundary

The rule is prospective and does not authorize reconstruction by naming
heuristics. GITHUB015 closed-structure enforcement became active at
`2026-09-13T10:02:02Z` with commit
`6f6794e24dc86a35dc1dc088c145ee18dd4f3c67`.

For formal Issues closed before that instant:

- explicit textual/form parent + no native parent -> deterministic native repair;
- existing native parent + contradictory stale text -> native parent prevails;
- no native parent + no explicit declaration -> legacy unresolved; do not guess
  and do not fail steady-state synchronization merely because the historical
  relation cannot be recovered.

For formal Issues closed at or after that instant, no grandfathering applies:
normal GITHUB015 postconditions remain fail-closed. This preserves the value of
the backstop for all new publication while avoiding a retroactive rewrite of
hundreds of pre-governance Issues.

## Decision approval provenance

Allocation and research do not select a Dxxx/PLATxxx. A ratification may be
published only after explicit owner approval of the exact candidate. Broad
commands such as `dale`, `continue`, phase progression, permission to repair
metadata, or permission to investigate are not transferable approval tokens.

An agent must never create an `Owner decision recorded` comment in order to make
its own recommendation appear approved. If exact approval provenance is missing
or ambiguous, keep the decision open at `status:needs-decision` and stop dependent
publication. Repository tooling cannot cryptographically distinguish every
human-vs-connector write under the same GitHub identity, so this invariant is
backed by explicit agent fail-closed policy and auditable publication evidence,
not by pretending GitHub actor identity proves conversational consent.

## Decision closure and durable publication

Exact owner approval and durable ratification publication are distinct
postconditions.

For a Dxxx/PLATxxx whose selected contract must be written to repository
governance, approval changes the lifecycle from waiting-for-decision to
publication work; it does not complete the Issue. While the ratification slice is
being prepared or retried, the Issue remains open, normally
`status:in-progress`, with its required owner and effective Priority.

A launcher/result that aborts before mutation, fails validation, fails
commit/push, or otherwise does not return the required `PUBLISHED` evidence
leaves `REQUIRED_DURABLE_PUBLICATION` unsatisfied. The Issue must remain open and
dependent work must remain blocked when the decision itself says ratification is
a release condition.

Closure as `completed` is valid only after:

1. exact owner approval provenance is already satisfied;
2. the bounded ratification publication succeeds;
3. `origin/main` is re-read and contains the expected durable decision record;
4. live Issue/Project state is re-read; and
5. every applicable publication postcondition passes.

A comment saying `owner approved`, an approved candidate in the Issue body, or a
prepared-but-unpublished patch is not a substitute for the durable record.

### PLAT034 fresh-chat closure regression

PLAT034 / #486 provided a live post-GITHUB015 reproduction of this boundary on
2026-09-13.

The Issue was created as a P0 PLAT decision gate and Candidate C-prime was
explicitly owner-approved, but #486 was closed `completed` seconds after creation
while its own contract still required durable ratification before PERF006-C3
could proceed. The first ratification launcher then explicitly reported that it
did not publish because of a launcher precondition defect.

The live repair sequence was:

```text
premature closed #486
    -> reopen
    -> family:PLAT
    -> status:in-progress
    -> priority:p0
    -> assignee:guillermomolina

ratification V2
    -> PUBLISHED
    -> origin/main commit
       49cd7fe8b883b415f11ae2c33eea11c2d393bd22
    -> durable PLAT034 record:
       RATIFIED — Candidate C′ selected
    -> live state re-read
    -> close #486 completed
```

This is the required fresh-chat reproduction evidence: approval alone did not
satisfy closure, a failed publication kept the decision incomplete, and closure
became valid only after durable publication and live re-verification.

## Phase transitions

When a workstream advances to a new formal child phase:

1. allocate/create the child with correct family, canonical status and owner;
2. establish and verify native parent;
3. resolve effective Priority after hierarchy exists;
4. project the resulting state;
5. only then report the phase transition complete.

If a substantive decision is exposed immediately, the child becomes blocked and
the Dxxx/PLATxxx gate is published as Needs decision with explicit ownership and
resolved scheduling Priority.

## Live acceptance cases

GITHUB015 closure requires re-verification of at least:

- #481 -> remains closed after its completed design/ratification path, while
  native parent #429 and `family:LIB` are structurally verified/reconciled; no
  lifecycle/assignee/Priority is manufactured by the repair;
- #482 -> native parent #262; effective P0 inherited while active;
- #483 -> decision/ratification provenance explicitly resolved by the owner;
- #486 -> PLAT034 durable ratification commit
  `49cd7fe8b883b415f11ae2c33eea11c2d393bd22` exists before final `completed`
  closure; and
- a fresh-chat formal publication reproduction cannot report success while any
  applicable postcondition is missing. PLAT034/#486 satisfies this reproduction
  for the decision-closure boundary.

## Validation

Validation class: `PROJECT_COORDINATION_TOOLING_ONLY`.

Required local validation:

```text
python3 scripts/issue_intake.py --self-test
python3 scripts/project_status_sync.py --self-test
git diff --check
```

Required live validation after publication:

1. run `Project status sync` manually once;
2. verify native parent endpoints for #481 and #482, including closed #481;
3. verify #481 stayed closed and received no manufactured lifecycle/assignee/
   Priority changes from structural repair;
4. verify inherited Project Priority for active #482 after native hierarchy exists;
5. verify #484 remains correctly routed as the active GITHUB015 work item;
6. repeat a bounded fresh-chat creation/phase-transition test before closure.

No Maven/runtime/spec test is required unless this slice unexpectedly changes
executable Protos/runtime code.

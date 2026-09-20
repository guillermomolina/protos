# Protos live GitHub coordination

These instructions apply to Issue/Project allocation, intake, hierarchy,
dependencies, status, priority, work log, closure, milestones, and community
routing for formal work.

Live GitHub coordination governed by these rules is agent-executable under
Human-executor mode (repository-root AGENTS.md). It is separate from
repository-content publication, which is human-executed. GitHub connector/API
write safety remains as defined in the repository-root AGENTS.md: capability
probes are read-only, sentinel artifacts are forbidden, and accidental
mutations must be stopped and reported.

## Native GitHub Issue hierarchy
<!-- GITHUB006 NATIVE-ISSUE-HIERARCHY-AUTHORITY -->

GitHub's native Issue parent/sub-issue relationship is the canonical **live
parent/child coordination structure** for formal Protos work items. Project
`Parent issue` and `Sub-issues progress` are derived presentation surfaces, not
independent hierarchy authorities.

When a formal child Issue has durable project identity under the granularity rule
below:

- establish its native parent relationship in the same coordination step as Issue
  creation when the available interface supports it, or link it immediately
  afterward before treating the child as fully reconciled;
- a textual `Parent: #N`, `Parent work item: #N`, checklist entry, title prefix,
  family label, Project field, or repository document is not a substitute for
  the native parent relationship;
- textual parent prose may remain when it is useful explanatory or historical
  context, especially on retrospectively migrated Issues, but live hierarchy
  queries MUST use the native relation;
- if textual parent prose and the native relationship disagree, stop and
  reconcile the conflict explicitly rather than silently choosing either source;
- if the current environment cannot create or mutate native sub-issue
  relationships, report that coordination limitation explicitly and leave the
  native-link step visibly pending; do not claim the hierarchy is reconciled
  merely because parent text was written;
- `family:<FAMILY>` remains orthogonal and continues to classify formal work
  ownership only; it MUST NOT encode parent/child structure; and
- changing a native parent relationship does not by itself change Status,
  Priority, assignee, design authority, specification authority, or publication
  state.

Existing historical Issue prose that says native sub-issue mutation was
unavailable at migration time does not require bulk rewriting after the native
relationship has been established. Preserve historical wording unless a bounded
Issue-content reconciliation explicitly owns that cleanup.

## GitHub Issue intake and creation
<!-- GITHUB007 ISSUE-INTAKE-GOVERNANCE -->

GitHub Issue forms are **intake surfaces**, not authority over Protos semantics,
design approval, work-family allocation, lifecycle state after intake, or
scheduling priority.

The repository distinguishes:

- **tracked project work** — maintainer-allocated formal identifiers;
- **bug reports** — community defect intake, not automatically `BUGxxx`;
- **documentation problems** — community documentation intake; and
- **community requests** — concrete adoption/example/tooling/packaging/ecosystem
  requests. Open-ended questions and language/design proposals belong in
  Discussions.

Form-created Issues start with `status:inbox`. No Issue form assigns
`priority:*`; GITHUB005's explicit-unset priority rule remains authoritative.

For a maintainer-authorized formal Issue, `scripts/issue_intake.py` may
mechanically reconcile only facts already determined by the formal identifier
and explicit hierarchy declaration:

- derive the one `family:<FAMILY>` label from the identifier prefix;
- preserve unrelated labels;
- verify the GITHUB006 native parent relation; and
- when no native parent exists, consume one unambiguous explicit `Parent: #N`,
  `Parent work item: #N`, or tracked-work `Parent issue` response only as
  bootstrap input to establish the native relation.

The helper MUST NOT infer a parent from dependencies, `Triggered by` prose,
similar names, family membership, or Project grouping. It MUST NOT replace a
conflicting existing native parent automatically.

A contributor cannot allocate formal Protos work merely by typing an
identifier-shaped title or selecting the tracked-work form. Automatic formal
reconciliation requires a maintainer-trusted author association (OWNER, MEMBER
or COLLABORATOR) or an already-applied `family:*` label that demonstrates
maintainer adoption. Untrusted formal-looking submissions remain ordinary
triage.

The intake helper does not infer `Ready`, `In progress`, `Needs decision`,
`Blocked`, `Paused`, `Review`, assignee, or Priority from Issue prose. Existing
GITHUB004/GITHUB005 status/priority synchronization remains the sole automated
Project projection path.

Agents creating formal Issues directly through GitHub APIs/CLI MUST still create
them correctly in the first place: apply the matching family label, the proper
live status/assignee when known, and the native parent in the same coordination
step when the Issue is a formal child. The intake workflow is a convergence
safety net, not permission to omit those obligations.

### Formal Issue publication transaction
<!-- GITHUB015 FORMAL-ISSUE-PUBLICATION-TRANSACTION -->

A direct formal Issue publication is a coordination transaction, not a successful
operation merely because GitHub returned an Issue number. Before reporting a
formal Issue, sub-issue, phase transition, or decision checkpoint as published,
the agent MUST re-read live GitHub state and verify every applicable postcondition:

```text
FORMAL_IDENTIFIER_UNIQUE=PASS
FAMILY=PASS
CANONICAL_STATUS=PASS
ASSIGNEE_INVARIANT=PASS
NATIVE_PARENT=PASS|NOT_APPLICABLE
EFFECTIVE_PRIORITY=RESOLVED|INTENTIONALLY_UNSET
PROJECT_ROUTING=PASS
DECISION_APPROVAL_PROVENANCE=PASS|NOT_APPLICABLE
DECISION_INVARIANT_CONSISTENCY=PASS|NOT_APPLICABLE
REQUIRED_DURABLE_PUBLICATION=PASS|NOT_APPLICABLE
```

A textual parent declaration is bootstrap input only. It never satisfies
`NATIVE_PARENT=PASS`. If the current connector cannot mutate the native relation,
use the repository intake reconciliation path and verify GitHub's native parent
endpoint afterward. If that still does not converge, the publication is
**incomplete** and MUST be reported as such rather than continuing as though the
hierarchy existed.

Closing an Issue does not erase these structural postconditions. A closed formal
child MUST remain attached through the native Parent/Sub-issue relation, and its
formal family/identifier structure remains auditable historical truth. Intake
automation MAY reconcile those structural facts after closure, but MUST NOT
reopen the Issue, manufacture an assignee, change lifecycle state, or invent/copy
scheduling Priority merely to repair closed structure.

The closed-structure rule is prospective, not permission to fabricate historical
hierarchy. For formal Issues closed before GITHUB015 enforcement became active at
`2026-09-13T10:02:02Z`, an explicit textual/form parent declaration may bootstrap
a missing native parent, but absence of both native parent and explicit
declaration is recorded as unrecoverable legacy structure and MUST NOT be guessed.
If such a legacy Issue already has a native parent that conflicts with stale
textual parent prose, the existing native relation prevails and MUST NOT be
rewritten. Formal closures at or after the enforcement instant remain fully
fail-closed under the normal native-parent postcondition.

For trusted formal work, lifecycle state must be explicit at creation time. The
neutral community `Inbox` fallback MUST NOT be used to hide a missing status on a
formal Issue. `status:in-progress` and `status:needs-decision` require an owner;
when otherwise unassigned, the repository owner is the coordination fallback.
Those two statuses also require a resolved effective scheduling Priority: an
explicit Priority for a top-level item, or an inherited Priority through the
nearest open native ancestor for a child. Never copy an inherited `priority:*`
label onto a child merely to make the invariant pass.

When a parent workstream advances to a new formal child phase, create and verify
the child hierarchy before treating the phase transition as complete. If that
child immediately exposes a substantive Dxxx/PLATxxx decision, leave the child
blocked and publish the decision gate with `status:needs-decision`, an owner, and
a scheduling Priority justified by the blocking work.

The design-approval gate is part of this transaction too. An agent MUST NOT post
`owner approved`, ratify/close a Dxxx or PLATxxx, release dependent work, or
publish a ratification record unless the project owner explicitly approved the
**exact candidate** in the active interaction or another auditable approval source.
Generic continuation words such as `dale`, `continue`, `go ahead`, or permission
to repair coordination state are never approval of an unrelated pending design.
The agent MUST NOT manufacture an owner-approval comment as provenance. If exact
approval provenance is absent or ambiguous, fail closed at `status:needs-decision`.

For a `Dxxx`/`PLATxxx` with recorded owner-approved invariants,
`DECISION_INVARIANT_CONSISTENCY=PASS` requires the GITHUB021 invariant/delta
check before exact-candidate approval is treated as sufficient for ratification.
A candidate that contradicts an applicable invariant without explicitly
reopening and re-approving that point MUST remain unresolved even when other
parts of the candidate were approved. Use `NOT_APPLICABLE` only when the decision
has no recorded owner-approved invariant to compare; do not use it to bypass a
known conflict or a missing check.

Owner selection and durable ratification are separate postconditions. When an
approved Dxxx/PLATxxx requires a durable ratification/publication record, that
record belongs in `guillermomolina/protos-project-docs`, and the Issue MUST remain
open until that publication succeeds. After exact approval,
`status:in-progress` is appropriate while the bounded ratification publication is
being prepared or retried; approval alone MUST NOT move the Issue directly to
`completed`.

A ratification launcher that aborts, fails validation, fails commit/push, or does
not return its required `PUBLISHED` evidence leaves
`REQUIRED_DURABLE_PUBLICATION` unsatisfied. The agent MUST NOT close the Issue,
release dependent work, or describe the decision as durably ratified merely
because the candidate is approved or an Issue comment records the approval.

Closure is allowed only after the required durable publication succeeds and the
agent re-reads the exact published project-record revision plus live Issue state.
For a Dxxx/PLATxxx closure that requires such a publication, the transaction is
therefore:

```text
exact owner approval
    -> DECISION_APPROVAL_PROVENANCE=PASS
invariant/delta consistency check
    -> DECISION_INVARIANT_CONSISTENCY=PASS|NOT_APPLICABLE
bounded publication to guillermomolina/protos-project-docs
    -> PROJECT_RECORD_REVISION=<exact SHA>
re-read exact durable record at PROJECT_RECORD_REVISION
    -> REQUIRED_DURABLE_PUBLICATION=PASS
re-read live Issue / Project state
    -> remaining publication postconditions PASS
close completed
```

When closure evidence spans both repositories, it MUST additionally identify the
exact `PROTOS_REVISION` and `PROJECT_RECORD_REVISION` and verify
`CROSS_REFERENCES=PASS`; a moving `main` reference is not revision-bound closure
evidence.

If the durable project record is not required for a particular formal item,
record `REQUIRED_DURABLE_PUBLICATION=NOT_APPLICABLE`; never silently treat a
failed or pending required publication as not applicable.

## GitHub release milestone governance
<!-- GITHUB008 RELEASE-MILESTONE-GOVERNANCE -->

GitHub Milestones are reserved for **concrete release targets**. They are not a
general work-classification or scheduling mechanism.

The repository separates GitHub responsibilities as follows:

- Project `Status` / `Priority` — live scheduling and lifecycle;
- native Issue parent/sub-issue relationships — formal work hierarchy;
- `family:<FAMILY>` — formal work-family classification;
- Milestone — one selected release target / release gate; and
- Git tag + GitHub Release — source identity and published deliverable for that
  version.

Agents MUST NOT use Milestones to represent work families, implementation
phases, generic backlog buckets, `Now` / `Next` / `Later`, priority, status, or a
second roadmap taxonomy.

Create a milestone only after the project owner has selected a concrete intended
release target. A milestone title uses the release version without the Git tag
prefix (for example `0.3.0`); the corresponding tag remains `v0.3.0` and the
release title may be `Protos 0.3.0`.

A due date is optional. Do not invent a date merely because GitHub supports one;
use it only when a real project or external commitment exists.

Assign an Issue or Pull Request to a release milestone only when its completion
is genuinely part of that release gate or explicitly planned release contents.
Do not propagate milestone membership mechanically through parent/sub-issue
hierarchy in either direction. Prefer the smallest set of release-significant
work items that makes milestone progress meaningful, and avoid counting one
deliverable twice through both a container parent and every child unless those
items independently gate the release.

Do not infer milestone membership from `family:*`, `status:*`, `priority:*`,
Project Roadmap, title prefixes, parent relations, implementation-version bumps,
or proximity to a release. Future release automation may validate a
project-owner-selected milestone, but it MUST NOT silently choose the target or
populate release membership.

Closing a milestone means the selected release outcome has been completed and
published, or has been explicitly abandoned/reconciled. Milestone completion
percentage alone is not authority to publish or close a release target.

Historical releases do not require retroactive milestones for symmetry. In
particular, the existing `v0.2.236` prerelease remains valid without a matching
historical milestone.

Do not create a future milestone such as `0.3.0` merely because it is the next
plausible semantic version. Selecting the next public release target remains an
explicit project-owner scheduling/release decision.

## GitHub Issue vs implementation-slice boundary
<!-- ISSUE-SLICE-BOUNDARY -->

A **GitHub Issue/Sub-issue** is an independently meaningful live coordination
unit. A **slice** is a bounded implementation/publication unit inside one Issue.
New decomposition remains a slice unless at least one promotion trigger applies:

1. **Independent closure** — it can satisfy/close its own acceptance criteria
   while the parent remains open.
2. **Independent blockage** — it can be blocked while a sibling can continue.
3. **Independent scheduling** — it can be owned/progress independently in
   parallel with a sibling.
4. **Issue dependency** — it must participate directly in a native
   `blocked by` / `blocking` relation.
5. **Decision checkpoint** — it owns an independently tracked project-owner
   approval or dedicated `Dxxx` / `PLATxxx` dependency.
6. **Multi-publication scope** — before implementation, it is expected to need
   at least three distinct publication slices; retries, reruns, launcher
   revisions, and repairs of the same slice do not count.

If any trigger applies, the work MUST have its own Issue and, when it belongs to
larger tracked work, a native Parent/Sub-issue relation. If none applies, it MUST
remain a slice. A bounded patch, retry, launcher revision, same-slice regression
repair, validation checkpoint, commit-only step, or purely mechanical
subdivision is therefore not an Issue unless it independently crosses a trigger.

If an existing slice later crosses a trigger, promote it then, preserve prior
slice evidence as history, and continue under the new Issue; do not rewrite
historical commits. Textual parent prose never substitutes for the native
relation. `Sub-issue progress` measures coordination milestones, not patch count.

Historical backfill is selective: create only useful coordination-level
milestones that satisfy a promotion trigger. Do not recreate every historical
micro-slice, manufacture fake work, or alter historical closure evidence.
Dependencies remain governed by the dependency policy below; semantic/platform
decisions remain governed by the explicit approval gate.

## Native GitHub Issue dependencies
<!-- GITHUB009 NATIVE-ISSUE-DEPENDENCY-AUTHORITY -->

Native GitHub `blocked by` / `blocking` relationships are the canonical live
graph for exact Issue-to-Issue blockers. They are orthogonal to hierarchy,
`status:*`, `priority:*`, and durable repository evidence.

When an exact current blocker is known, establish the native edge when supported.
Textual `Blocked by`/`Prerequisite` prose, checklists, titles, family, Project
grouping, hierarchy, roadmap position, identifier ordering, `Triggered by`, or
`status:blocked` do not substitute for or imply that edge. Do not propagate edges
through hierarchy or copy transitive prerequisites; retain one native edge for
each independent direct blocker.

If native dependency state conflicts with current explicit coordination or
durable evidence, reconcile the conflict explicitly. If dependency mutation is
unavailable, report the exact pending edge instead of treating prose as
reconciled.

`status:blocked` says that lifecycle progress is blocked; a native edge identifies
a specific Issue blocker. External/non-Issue blockers therefore need no invented
edge. Closing a blocker neither chooses the dependent Issue's next status nor
requires deleting the historical edge; transition lifecycle state only from
current coordination evidence.

Dependency automation may verify explicit/native state and report drift, but MUST
NOT infer blocker identity, close dependents, or choose lifecycle transitions.

## Live GitHub assignee discipline
<!-- LIVE-GITHUB-ASSIGNEE-DISCIPLINE -->

GitHub assignees represent active responsibility, not repository ownership, and
MUST NOT be mirrored into historical repository ledgers.

For directly owned formal work:

- `In progress` and `Needs decision` MUST have an active responsible assignee.
- When acting for the repository owner and no different human is already
  responsible, use `guillermomolina` as the fallback.
- A newly created Issue moved immediately into an ownership-requiring state MUST
  receive its assignee in the same coordination workflow.
- `Inbox` and unclaimed `Ready` normally remain unassigned.
- Preserve an existing responsible human; assistance alone is not grounds to
  replace or add another assignee.
- Child activity does not assign its parent unless the parent itself is actively
  owned.
- `Blocked` and `Review` may retain an assignee but MUST NOT manufacture one from
  status alone.
- Closing an Issue does not require clearing historical assignment.

Repository synchronization MAY add the owner fallback only when an
`In progress`/`Needs decision` Issue has no assignee; it MUST preserve an
existing assignee and fail visibly if fallback assignment is rejected.

Agents with assignee-mutation capability MUST perform required reconciliation.
Without that capability, report the pending coordination step; that limitation
alone is not an implementation blocker.

## Native parent priority inheritance
<!-- GITHUB005 NATIVE-PARENT-PRIORITY-INHERITANCE -->

Effective priority resolves in this order:

1. the Issue's own explicit `priority:*`;
2. otherwise the nearest **open** native ancestor with an explicit `priority:*`;
3. otherwise unset.

An Issue label is therefore an explicit override, not a copied parent value.
Agents MUST NOT duplicate parent priority onto children merely for Project
ordering. Closed ancestors do not provide live inheritance; parentless work
remains unset unless explicitly prioritized; Status never manufactures priority.

After an explicit priority change, repository automation MUST reconcile the
changed Issue and affected native descendants while preserving explicit-vs-
inherited identity. Closing or reopening a formal Issue MUST likewise reconcile
its open native descendants so a Project Priority inherited only from a now-closed
ancestor is cleared instead of becoming stale or being materialized as an
explicit child priority. Repository-wide reconciliation is an explicit
audit/repair operation, not the routine propagation path.

## Hierarchy-independent actionable Work queue
<!-- GITHUB025 HIERARCHY-INDEPENDENT-WORK-QUEUE -->

Native hierarchy records provenance and coordination structure; it does not decide
whether an open Issue is actionable.

The main Protos Development **Work queue** MUST include open Issues whose Project
Status is `In progress`, `Review`, or `Ready` regardless of whether they are
top-level or native sub-issues and regardless of whether their ancestors are open
or closed. Closing a decision/audit parent therefore does not hide a still-open
implementation child.

The canonical base saved-filter intent is:

```text
repo:guillermomolina/protos is:issue is:open status:"In progress",Review,Ready
```

`no:parent-issue` MUST NOT be part of the Work queue filter.

Status remains the actionability authority. Native Parent/Sub-issue remains the
hierarchy authority. Priority remains an independent scheduling dimension under
GITHUB005; an actionable `Ready` child may remain visible with Priority unset
after its last priority-bearing open ancestor closes.

Repository Project synchronization MUST verify the saved **Work queue** filter
against this contract and converge it when the available Project token permits
view mutation. Drift is a visible synchronization failure, not permission to
detach children, reopen completed ancestors, copy ancestor priorities onto
children, or invent lifecycle transitions.

Independently ratified routing exclusions remain applicable. In particular,
Community placement below is not replaced by this hierarchy rule.

## Community contribution placement
<!-- GITHUB012 COMMUNITY-WORK-QUEUE-PLACEMENT -->

Community suitability is a contribution classification, not a hierarchy rule and
not an automatic scheduling priority. The Issue-owned `community` label is the
durable routing marker for the dedicated Community Project view.

Agents MUST preserve these distinctions:

- do not create a synthetic parent/umbrella Issue merely to group unrelated work
  because several tasks are suitable for community contribution;
- when a community-facing task has a genuine semantic workstream parent, use the
  native Parent/Sub-issue relationship under the ordinary hierarchy rules;
- when no genuine semantic parent exists, the task MAY remain top-level;
- a top-level Issue carrying `community` is intentionally excluded from the main
  Work queue and remains visible through the dedicated Community view;
- the Community view MUST include open `community` Issues across hierarchy levels,
  so native sub-issues are not hidden merely because they have a parent;
- Project `Area=Community` may be useful metadata but MUST NOT be treated as the
  durable routing authority; `community` label membership is Issue-owned and
  survives Project-field drift;
- do not infer Community membership from `good first issue`, `help wanted`,
  `examples`, documentation, adoption or another adjacent label; apply/remove the
  `community` label deliberately;
- do not infer `priority:p3` from Community membership; Priority remains an
  independent scheduling dimension and a community Issue may legitimately be
  P0/P1/P2/P3; and
- do not use Priority as a substitute for view routing. P3 means
  opportunistic/later scheduling, not "Community".

For active external contributions, an Issue assignee and the actual patch author
may differ because GitHub can reject an external account as an eligible Issue
assignee. When an external linked PR clearly owns the implementation but the
author cannot be assigned, an owner fallback assignee represents repository
coordination only. Agents MUST NOT describe that fallback as authorship or as the
external contributor's work having been taken over. Preserve/link the PR and use
the Project's `Linked pull requests` field as the execution-context signal.

### GitHub-native project coordination

GitHub owns live project coordination, not language/design authority or durable
repository evidence. The dedicated hierarchy, dependency, assignee, family,
status, priority, work-log, and closure sections own their respective mechanics.

Normative semantics remain under `spec/`; ratified `Dxxx`/`PLATxxx` decisions and
`Bxxx` blocker conditions retain their durable repository authority. GitHub
Issues represent formal work and live coordination, while `Protos Development`
is a derived scheduling/dashboard projection. No Discussion, Issue, Project
field, reaction, vote, PR, or merge satisfies the explicit project-owner design
approval gate by itself.

<!-- GITHUB001-F LEGACY-LIVE-LEDGER-RETIREMENT -->
`guillermomolina/protos-project-docs:docs/project/history/OPEN_TASKS.md` is a
retired historical backlog snapshot; do not add actionable work to it, mirror
GitHub into it, or use it to choose current work.

`guillermomolina/protos-project-docs:docs/project/registries/IMPLEMENTATION_STATUS.md`
is durable implementation/closure evidence only. It MUST NOT mirror live status,
assignee, priority, or roadmap state, and it never overrides the owning Issue.
Preserve both legacy files as historical evidence; do not bulk-delete them.

Before actionable implementation/project work, apply the **Minimum investigation
floor** in the repository-root AGENTS.md: inspect the current Issue when one
exists, current `origin/main`, owning durable records, blockers, and applicable
specification sources. Issue state is coordination data, not proof of repository
fact or semantics. Reconcile stale Issue claims to published authority, and use
the dedicated status/priority policies for live transitions; Project projection
follows automatically.

When a bounded implementation slice is successfully published, its Protos
publication commit MUST still record every durable artifact owned by
`guillermomolina/protos` and required by the owning work item (for example
implementation version/changelog, source, tests, specification changes, or other
product-repository artifacts where applicable).

A durable project record owned by `guillermomolina/protos-project-docs` MUST NOT
be forced into that same commit. When such a record is required, first publish
the product commit and establish `PROTOS_REVISION=<exact SHA>`, then publish the
durable record in `protos-project-docs` naming that exact revision, establish
`PROJECT_RECORD_REVISION=<exact SHA>`, and verify the required cross-references
before treating `REQUIRED_DURABLE_PUBLICATION` as satisfied. Failure of the
second publication does not roll back the product commit; the owning Issue
remains open until the durable publication postcondition is satisfied.

Repository-content publication and live GitHub coordination are separate
operations. Helpers handed to the human for repository-content changes MUST NOT
require GitHub API credentials and MUST NOT create, close, relabel, assign, or
move Issues/Project items. Live GitHub coordination remains a separate
post-publication step.

Only after the applicable publication postconditions are confirmed as successful
may the coordinating agent update the corresponding GitHub Issue live state. If
the agent cannot perform a required Issue write, it must report the exact Issue
coordination update still required rather than pretending it happened.
Project-field updates are not required by this rule.

<!-- GITHUB001 ISSUE-WORK-LOG-CHANGELOG-DISCIPLINE -->
#### Issue work log and changelog discipline

For actionable work, the most specific owning GitHub Issue is the live execution
diary. Agents with Issue-write capability MUST record materially significant
outcomes after they are known, including successful publications, meaningful
failed/unpublished attempts, validation/diagnostic results that affect work
state, approved design decisions with links to their durable authority, and
dependency/child outcomes that change what is actionable next.

Update a parent only when the outcome materially changes that parent's live state
or summary. Mechanical slices without their own Issue remain logged on their
owning Issue when significant.

Issue comments MUST summarize scope, stable publication/evidence identities,
relevant PASS/FAIL results, resulting state, and next action where useful; do not
paste raw terminal/build logs. Suggested headings such as `WORK UPDATE`,
`BLOCKER`, `PUBLICATION`, and `DECISION` are presentation only, not lifecycle
states.

`CHANGELOG.md` records published durable changes to Protos and maintained
artifacts; it is not an execution diary. Failed/unpublished attempts,
validation-only activity, diagnostics without publication, live
status/assignee/priority/roadmap changes, and dependency/blocker transitions
without a durable artifact do not require changelog entries merely because they
occurred. Conversely, a materially published implementation, specification,
documentation, tooling, governance, or other maintained-artifact change may
require one even while parent work remains open.

When one publication changes both repository artifacts and live Issue state, the
changelog records what changed in the published project; the Issue records the
execution/coordination outcome. Post the Issue outcome only after publication is
confirmed. If Issue mutation is unavailable, report the exact pending update.

<!-- GITHUB020 FORMAL-WORK-CLOSURE-EVIDENCE -->
#### Formal work closure-evidence gate

Before closing formal work, the coordinating agent MUST establish:

```text
ISSUE_CLOSURE_COMMENT=PASS
CLOSURE_EVIDENCE_IDENTIFIED=PASS
DURABLE_RECORD_DECISION=REQUIRED|NOT_REQUIRED
PROJECT_RECORD_REVISION=<exact SHA>|NOT_APPLICABLE
REQUIRED_DURABLE_PUBLICATION=PASS|NOT_APPLICABLE
```

Every formal closure requires a compact final Issue comment identifying the
stable publication, validation, decision, external evidence, or other identities
that justify closure. A successful commit, passing test, Project `Done` state, or
closed Issue is not by itself complete closure evidence.

A separate durable project record is conditional:

- use `DURABLE_RECORD_DECISION=REQUIRED` when the work-item contract requires
  one or when closure depends on project knowledge not adequately recoverable
  from published product artifacts, maintained documentation, exact Git history,
  tests, and the Issue history;
- otherwise use `DURABLE_RECORD_DECISION=NOT_REQUIRED` only as an evidence-backed
  conclusion, not as a bypass;
- do not create one Markdown record per Issue merely for symmetry.

When required, maintained work-item records belong under
`guillermomolina/protos-project-docs:docs/project/work/<formal-work-item>/`;
snapshot-like evidence belongs under
`guillermomolina/protos-project-docs:docs/project/evidence/<formal-work-item>/`
only when retaining it is useful. Prefer stable run/artifact/commit/release
identities over copied raw logs.

Revision-coupled records MUST name exact revisions, never a moving `main`. After
publication, record the exact `PROJECT_RECORD_REVISION` and every other revision
required by the closure claim, re-read the published record, and keep the owning
Issue open until `REQUIRED_DURABLE_PUBLICATION=PASS`. A prepared file, failed
helper, unpushed commit, Issue comment, or Project field does not satisfy that
postcondition.

Project metadata is not closure-evidence authority and MUST NOT substitute for
the final Issue summary or a required durable publication. Do not create a
Project `Evidence` field merely to mirror repository/Issue evidence.

Stronger closure contracts remain stronger, including Dxxx/PLATxxx approval and
ratification, explicit work-item closure contracts, release evidence, and
cross-repository publication requirements.

This rule is prospective. Do not bulk-create durable records for historical
closed Issues merely for symmetry; reconcile an older closure only when current
bounded work identifies durable project knowledge still worth preserving.

Repository publication follows the publication and repository-content safety
rules in the repository-root AGENTS.md. Live GitHub coordination does not reserve
mutable repository files or semantics; agents MUST still request a
synchronization with current `origin/main`, inspect overlaps, and preserve
concurrent work.

#### Discussions and actionable work

Use a Discussion while the primary activity is exploration: understanding a
problem, collecting use cases, comparing alternatives/prior art, or preparing a
decision packet. Once there is bounded actionable project work with sufficiently
settled semantics/architecture, track that work in an Issue. A Discussion may
remain as historical reasoning and link to the resulting Issue/decision record;
it does not become normative authority.

When an unresolved substantive design choice appears during an Issue, do not
silently settle it in the Issue. Route the choice through the existing design
process and explicit approval gate; link the resulting `Dxxx`/`PLATxxx`/other
repository authority back from the Issue as useful coordination metadata.

#### Tracked work-item families

Formal identifiers use the family meanings already established by current
repository records and GitHub history; identifier-shaped text or chat memory does
not establish authority.

Every newly allocated formal Protos identifier, including `Dxxx`, `PLATxxx`, and
`Bxxx`, MUST have a GitHub Issue as part of allocation. The Issue owns live
coordination; durable decision/blocker authority remains in its repository
record. A formal identifier MUST NOT exist only in chat. When it also owns a
durable repository record, persist that record no later than the first
publication that materially establishes the work.

<!-- GITHUB001-A2 FAMILY-IDENTIFIER-POLICY -->
<!-- GITHUB001-A2 FAMILY-CLASSIFICATION-SINGLE-SOURCE -->
##### Family labels

Every Issue representing a formal identifier MUST carry exactly one
`family:<FAMILY>` label matching the identifier prefix. That label is the single
source of truth for formal family classification; do not create or maintain a
duplicate Project `Family` field.

Family labels classify formal work only. They MUST NOT encode status, priority,
roadmap position, assignee, blocking, or hierarchy. Ordinary community Issues
without a formal identifier need no family label.

`Dxxx`, `PLATxxx`, and `Bxxx` retain their repository-owned authority despite
their Issue representation; Issue/Project state, reactions, comments, or labels
cannot ratify, redefine, or clear that authority. GitHub Issue numbers and
Protos formal identifiers are independent namespaces.

<!-- GITHUB004 ISSUE-OWNED-PROJECT-STATUS -->
##### Issue-owned status and derived GitHub Project projection

The owning Issue is the canonical live-state source; `Protos Development` is a
derived dashboard. Every open actionable Issue MUST carry exactly one canonical
status label:

- `status:inbox`
- `status:ready`
- `status:in-progress`
- `status:needs-decision`
- `status:blocked`
- `status:paused`
- `status:review`

`status:paused` is distinct from `status:blocked`; a closed Issue projects to
Project `Done` regardless of its retained open-state label. When live state
changes, replace the `status:*` label coherently. New Issues default to
`status:inbox` only when no more specific state is known. Unknown `status:*`
labels are invalid drift and MUST fail closed rather than being ignored or
normalized silently.

Repository automation owns Project membership and Status projection. Agents
SHOULD NOT mutate Project Status merely to mirror Issue state. Project-sync
failure is a visible coordination failure, not repository/design authority; if
Issue mutation itself is unavailable when a transition is required, report the
pending Issue update.

<!-- GITHUB005 ISSUE-OWNED-PROJECT-PRIORITY -->
##### Issue-owned priority and derived GitHub Project projection

Priority is optional explicit Issue-owned scheduling metadata. An Issue may carry
at most one of:

- `priority:p0` — immediate/critical; exceptional;
- `priority:p1` — next/high priority;
- `priority:p2` — normal planned work;
- `priority:p3` — opportunistic/later.

No explicit/inherited priority means Project Priority is unset. Priority is
orthogonal to Status: never infer P0/P1/P2 merely from `Needs decision`,
`In progress`, or `Ready`. Preserve existing priority unless current coordination
evidence or an owner instruction justifies changing it.

An explicit priority change replaces the Issue's prior `priority:*` label.
Removing the only explicit label clears Project Priority only when no inherited
priority applies. During migration, if an open Issue has no explicit/inherited
label but still has a valid historical Project P0-P3 value, automation MUST
preserve that value and materialize the matching label; do not guess a priority
that is already lost.

Closed Issues may retain their last explicit priority and reuse it on reopen.
Priority never approves design, clears blockers, reserves files, or authorizes
publication. Repository automation owns Project Priority projection; agents
SHOULD NOT mutate Project Priority merely to mirror Issue coordination.

##### Collision-safe formal identifier allocation

Top-level family numbers are monotonic and never reused; historical, closed,
cancelled, superseded, or retrospectively imported allocations remain consumed.
Slice suffixes do not allocate another top-level number.

Before allocating a top-level identifier, the coordinating agent MUST:

1. confirm the work belongs to that family and merits new durable identity rather
   than a Discussion, ordinary Issue, existing parent, or slice;
2. search current durable repository records and relevant open/closed GitHub
   Issues for allocations in that family;
3. choose the next number after the greatest allocated top-level number;
4. create the Issue immediately as `<IDENTIFIER> — ...` with its matching
   `family:<FAMILY>` label; and
5. re-search the identifier after creation before treating allocation as
   confirmed.

The post-create search is the concurrency gate. If two Issues race for the same
previously-free identifier, the lower GitHub Issue number keeps it and later
collisions MUST re-scan, take the then-next number, rename, and re-check until
unique. A pre-existing durable repository allocation always wins over a new
GitHub collision.

`scripts/issue_intake.py` enforces the GitHub half by scanning authorized formal
Issue titles across open and closed Issues and failing closed on exact duplicate
identifiers. It does not replace the durable-repository search.

Do not publish a durable record, changelog/source reference, or implementation
under a newly allocated identifier until the uniqueness check passes. If Issue
creation/verification is unavailable, report the limitation rather than reserve
the identifier only in chat or a local patch.

Historical backfill preserves the established identifier and identifies itself
as retrospective; never renumber historical work for contiguity or to match
GitHub Issue numbers, and never fabricate historical dates/evidence. Family
semantics remain those documented by their owning policies.

Parent Issues remain open until their repository-defined outcome is complete.
A parent MAY close while independently meaningful descendants remain open when
the parent's own repository-defined outcome is complete. Do not keep a completed
AUDxxx, Dxxx, PLATxxx, or other parent artificially open merely to preserve
descendant visibility in Project scheduling; native hierarchy preserves
provenance while each descendant's own Status governs its lifecycle and
actionability.

Sub-issues require independently meaningful work; temporary patch versions,
diagnostics, launcher attempts, and cost-driven micro-slices do not become
project work merely because they helped execution.

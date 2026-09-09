Protos Agent Guidelines

Project purpose

Protos is an experimental prototype-based programming language designed from first principles.

The project deliberately separates language design from implementation. The implementation must realize the language defined by the specification; it must not silently redefine the language to accommodate implementation convenience.

When behavior is unclear, incomplete, or contradictory, do not invent semantics. Report the ambiguity and ask for a design decision.

## Design authority and explicit approval gate

The user/project owner is the final authority on when a substantive design
investigation is sufficient and when a design choice becomes selected, closed,
or ready to become project or language policy. Agents are expected to research,
compare, falsify, and recommend designs; they MUST NOT treat their own confidence
or completeness judgment as approval to close the design. This applies even when
an agent believes one alternative is clearly superior or that all competing
alternatives have been eliminated.

Unless the user explicitly delegates a specific bounded design decision to the
agent, a substantive design decision requires explicit user selection or
approval before the repository may treat it as settled.

For this rule, a substantive design decision includes a choice that does one or
more of the following:

- changes, narrows, or newly defines observable Protos semantics, syntax, public
  protocols, public Standard Library/tool behavior, or compatibility promises;
- selects capability, authority, ownership, lifetime, cancellation, resource,
  identity, equality, hashing, canonicalization, persistence, distribution,
  concurrency, ordering, isolation, security, or portability semantics;
- selects a durable architecture that materially constrains later
  implementations, tools, packages, libraries, runtimes, or ecosystem behavior;
- moves a design question from exploratory, `OPEN`, `PENDING`, or blocked state
  to `SELECTED`, `READY`, `CLOSED`, or equivalent settled state;
- creates or closes a `Dxxx` resolution, changes normative specification text to
  resolve a design question, or changes a blocker from unresolved to ready on
  the basis of newly selected semantics; or
- embeds an unresolved design choice inside an `Ixxx`, `LIBxxx`, `TOOLxxx`,
  `CLIxxx`, `PERFxxx`, documentation, distribution, or other implementation
  slice as though that choice had already been approved.

Routine implementation machinery that cannot change observable behavior and
does not establish a durable architectural constraint does not require a design
approval checkpoint. Agents may also decompose already-approved work into
implementation slices, choose local names/internal representations consistent
with existing rules, and make other mechanically necessary implementation
choices. Slice autonomy is not design authority: if decomposition exposes a new
substantive choice, that choice crosses this approval gate before a dependent
slice proceeds.

Before requesting approval, the agent SHOULD do enough work to make the user's
decision useful rather than merely returning an unexplored question. For a
substantive choice, present a decision packet that normally includes:

1. the exact problem and why a decision is needed;
2. relevant current Protos constraints and previously approved decisions;
3. meaningful alternatives and prior art from comparable languages, runtimes,
   ecosystems, or systems where applicable;
4. trade-offs, failure modes, scaling/security/lifetime consequences, and
   attempted counterexamples;
5. the agent's recommendation and why;
6. downstream consequences for specification, implementation, compatibility,
   blockers, and later work; and
7. any choices that remain intentionally deferred.

The recommendation MUST be labelled and treated as a proposal pending user
approval. Research completeness, a strong recommendation, passing tests, an
existing exploratory document, a previously published implementation slice, or
an apparently obvious choice does not turn the proposal into a project decision.

Do not infer design approval from a broad instruction such as "implement X",
"continue", "go ahead", "complete Ixxx", or permission to investigate a problem.
Do not infer it from silence, lack of objection, approval of an earlier slice,
permission to commit/push unrelated work, or an agent's earlier self-authored
status text. Approval applies only to the substantive choices that were actually
presented or otherwise explicitly delegated. If a materially new choice appears
later, present that new choice before crossing its decision boundary.

The user may explicitly delegate a bounded decision (for example, "decide this
one yourself after the comparison"). In that case the agent may select within
that stated scope, SHOULD record the delegated scope and rationale in the
resulting design record, and MUST NOT generalize the delegation to adjacent or
future design questions.

An agent MAY allocate a design identifier or blocker to record an unresolved
question when repository tracking rules require it, but such a record must stay
clearly unresolved (`OPEN`, `PENDING`, `BLOCKED`, or equivalent) until the design
is explicitly approved. Allocation is not selection.

Already-normative, already-closed repository decisions remain authoritative for
ordinary implementation work and do not require repeated approval merely because
a new agent encounters them. If the user explicitly asks to re-evaluate or
reopen such a decision, treat it as open for the requested review and do not
advance dependent new design work until the user approves the resulting choice.

## Project decision families

Formal decision identifiers distinguish normative language design from durable
platform-specific implementation architecture:

- `Dxxx` records language/specification design decisions whose authority is or
  may become normative for observable Protos behavior. A `Dxxx` resolution is
  implementation-independent even when a particular implementation exposed the
  question.
- `PLATxxx` records durable, non-normative implementation architecture decisions
  that depend on a concrete host platform/runtime/VM/OS/native substrate,
  materially constrain later implementation work, and are intended to remain
  semantically invisible to Protos programs.

Both families cross the explicit approval gate above: allocating an unresolved
identifier is allowed, but moving it to a selected/ratified state requires
explicit project-owner approval unless that bounded decision was explicitly
delegated.

Do not use `PLATxxx` as an escape hatch for semantic decisions. If a
platform-dependent choice changes observable Protos identity, authority,
ordering, isolation, failure behavior, portability guarantees, or another
language contract, resolve the semantic question through the applicable
`Dxxx`/normative specification process first. Conversely, do not force a
Truffle/JVM/OS/backend hosting choice into `Dxxx` merely because it is durable
when another conforming implementation could realize the same Protos semantics
with different machinery.

`Ixxx`, `CLIxxx`, `TOOLxxx`, `PERFxxx`, `DISTxxx`, and similar implementation or
project-work families may consume a ratified `PLATxxx` decision. The decision
record owns the durable platform architecture; the consuming work item owns its
implementation, tests, migration, and closure evidence.

The canonical registry for platform/runtime architecture decisions is
`docs/project/PLATFORM_ARCHITECTURE_DECISIONS.md`.

## Current project coordinates

The following identifiers are the repository's current operational coordinates.
They are exact spellings, not names that agents should reconstruct from a
person's name, memory, convention, or an approximate prompt:

- GitHub repository: `guillermomolina/protos`
- Maven groupId: `com.guillermomolina`
- Java package root: `com.guillermomolina.protos`
- Java source/test package path root: `com/guillermomolina/protos`

In particular, `guillermolina` (with only one `mo` sequence across the joined
surname boundary) is not a valid current repository owner, Maven coordinate,
Java package root, or package-path component for this project. Do not silently
introduce that spelling or another guessed variant.

When generating repository URLs, package declarations, imports, source paths,
license-header URLs, scripts, Maven coordinates, or other project identifiers,
prefer copying the current coordinate from authoritative repository state over
typing it from memory. Relevant sources include the configured Git remote,
`pom.xml`, and existing canonical package declarations/paths. If those sources
disagree, report the inconsistency instead of guessing which spelling is
intended.

These coordinates are implementation/project metadata, not Protos language
semantics and not a permanent claim about project ownership. A future move to a
GitHub organization, Maven namespace, or Java package root is allowed, but it
must be an explicit migration. Such a migration must update this section and
all affected repository references coherently in the same planned change; until
then, agents must preserve the exact current coordinates above.

<!-- BEGIN PROTOS DESIGN PHILOSOPHY -->
## Protos design philosophy

These principles apply to the whole repository. They constrain language design,
implementation architecture, libraries, tests, and documentation.

### Build a small universe, not a collection of features

Protos aims for a small conceptual foundation from which powerful behavior can
emerge. Minimize total conceptual complexity, not the number of named concepts.
A new abstraction is justified when it removes more independent rules, special
cases, or overloaded responsibilities than it introduces.

### Prefer mechanisms over institutions

Prefer general mechanisms from which higher-level abstractions can be built over
adding each useful abstraction as a permanent language-level category. Do not
promote a common pattern to a primitive merely because it is familiar or useful.
Programs should build institutions inside the Protos universe whenever the
existing mechanisms can express them naturally.

### No pets

Avoid individually privileged objects, values, contexts, constructs, or runtime
entities. Behavior should follow from semantic properties and general rules, not
from identity-based exceptions.

Prefer:

    Any object satisfying X behaves as Y.

over:

    This particular object behaves as Y.

Magic objects, privileged instances, distinguished roots, well-known singleton
entities, or identity checks must earn their existence through fundamental
semantics, not implementation convenience. Internal optimizations may recognize
specific objects only when observable behavior remains unchanged.

### Ordinary things should remain ordinary

Language facilities should remain ordinary participants in the object model
wherever possible. Do not create parallel semantic universes for contexts,
callables, built-ins, libraries, or runtime facilities when ordinary Protos
mechanisms can represent them.

### General rules beat special cases

Prefer rules that explain multiple behaviors naturally. Treat "except when",
"only for this object", "normally, but", and similar clauses as design smells.
Exceptions are sometimes necessary, but they require semantic justification.
Implementation convenience is not sufficient justification.

### Preserve orthogonality and composability

Features should compose through their existing semantics. A new feature should
not normally require pairwise special rules with objects, slots, delegation,
lookup, assignment, contexts, closures, calls, errors, mutation, concurrency, or
shared state. A large interaction matrix is evidence that the abstraction may be
wrong.

### Semantic distinctions should remain visible

Do not collapse semantically different operations merely because their
implementations are similar. If operations have different invariants, effects,
lookup behavior, or failure modes, that distinction should remain visible where
it improves reasoning. Do not make the runtime guess programmer intent.

### Fail where the invariant is violated

Invalid operations should fail at the point where the language invariant is
violated. Do not silently turn errors into absence or fallback behavior merely
for convenience. `null` is not a universal substitute for failure.

### Pay only for what you use

Protos must be useful from a trivial single-threaded `hello world` to highly
concurrent and very large deployments without imposing the cost of unused
capability.

Support for concurrency, distribution, clustering, isolation, scheduling, or
other advanced facilities must not impose unnecessary conceptual, syntactic,
runtime, memory, synchronization, or coordination cost on programs that do not
use them.

Simple things must stay simple; complex things must remain possible; programs
should pay only for the complexity they actually use.

### Scale by composition, not by changing universes

Protos should scale by composing the same underlying mechanisms rather than by
requiring a fundamentally different programming model at larger scale. New
layers are justified when they resolve a real semantic, lifecycle, isolation, or
scalability boundary while preserving the simplicity of lower layers.

The best scaling abstractions should feel like missing pieces of the existing
model, not foreign subsystems attached to it.

### Generality must be earned

Do not add abstraction merely in the name of generality. A design earns
generality by surviving substantially different realistic use cases with the
same small set of semantics. Prefer the simplest abstraction that solves the
motivating problem, composes with existing mechanisms, survives unrelated
scenarios, and does not impose significant cost on simpler programs.

### Minimize shared mutable state

Minimize shared mutable state in both language semantics and library design.
Prefer local state, immutability, isolation, ownership, message passing, or
other less-coupled mechanisms when they provide equivalent semantics. Narrow
the scope and lifetime of state that genuinely must be shared. Avoid global
mutable state.

The default question is:

    Can this state remain local?

If yes, keep it local.

### Prefer independence over coordination

When several semantically valid designs exist, prefer the one that allows the
most independent progress and requires the least necessary blocking and
coordination. Avoid serializing unrelated work merely because serialization is
easier to implement.

This does not mean mandatory asynchrony or lock-free algorithms at any cost. A
simple synchronous operation on local state can be preferable to additional
concurrency machinery. Minimize total coordination and complexity, not the raw
number of locks.

### Respect qualitative thresholds

Not all changes of size one have the same conceptual cost. Crossing from zero
instances of a mechanism to one instance can change the nature of the language.
Treat the first reserved word, privileged entity, implicit conversion, hidden
heuristic, platform-dependent semantic branch, or similar new category as an
architectural decision.

The distance from 0 to 1 may be greater than the distance from 1 to 10.

### Preserve global language properties

Before accepting an apparently local change, ask whether it creates a category
of thing that Protos previously did not have. Protect global properties such as
the absence of reserved words or unnecessary privileged entities unless the
benefit clearly justifies crossing that qualitative threshold.

### Solve ambiguity structurally before adding syntax

When syntax is ambiguous, first try to resolve the ambiguity by making the
grammar more precise, using existing delimiters, structural rules, or explicit
formal disambiguation. Do not add keywords, punctuation, or markers solely to
make parsing easier. Parser convenience is not sufficient reason to expand the
surface language.

### Keep platform differences at the boundary

Define portable semantics wherever a coherent portable abstraction exists.
Operating-system, host-runtime, JVM, Windows, Linux, or POSIX differences should
not leak unnecessarily into the core language model. Encapsulate genuine host
differences behind explicit system boundaries and capabilities.

Portability does not mean designing to the weakest common denominator. Define
the strongest coherent portable abstraction Protos can guarantee; when a
capability is genuinely platform-specific, make that dependence explicit rather
than accidental.

### Semantics before syntax; semantics before implementation

First define the capability, semantics, invariants, interactions, failure
behavior, and concurrency implications. Only then design syntax and
implementation. Truffle, GraalVM, the JVM, the parser, tests, or current runtime
behavior do not define Protos semantics.

### The specification defines Protos

The normative specifications under `spec/` define Protos. Everything else
implements, tests, explains, records, or uses them.

Not every file under `spec/` is normative. Design ledgers, changelogs, agent
instructions, OPEN/PENDING material, and exploratory documents do not become
language semantics merely by residing under `spec/`.

If implementation, tests, documentation, or historical behavior disagree with
normative specification, investigate the contradiction rather than silently
choosing the implementation.

### Observable behavior matters; machinery does not

Specify observable semantics precisely without unnecessarily prescribing
implementation machinery. Implementations may change representation, caching,
compilation, scheduling, dispatch, storage, or other internals as long as
observable Protos semantics remain unchanged.

### Concurrency is language semantics

Real multithreading and shared-state behavior must have explicit Protos
semantics where observable. "The JVM handles it" is not a Protos concurrency or
memory model.

### Learn from precedent; do not be ruled by it

Other languages, runtimes, specifications, research, and implementations are
evidence. They reveal known solutions, failures, edge cases, and trade-offs.
They are not authority over Protos. Familiarity and popularity are subordinate
to coherence with the Protos universe.

<!-- END PROTOS DESIGN PHILOSOPHY -->

## Protos source style: idiomatic syntax and canonical forms

For hand-written Protos source, agents MUST treat stable, specified idiomatic
syntactic forms as the default spelling for ordinary code instead of
systematically expanding them to their canonical protocol/desugared form merely
because that form explains the semantics.

This applies to Standard Library source, ordinary Protos tools/programs,
tutorials, examples, documentation snippets, and Protos-source tests whose
purpose is not the lowering itself. Use the explicit canonical/protocol form when
implementing or testing that mechanism directly, when bootstrap/layering would
otherwise become circular, when reflection/dispatch is the subject, or when the
explicit form is materially clearer.

This is a source-style rule, not language authority. It does not approve new
syntax, change specified equivalence, or permit an agent to invent sugar for an
unresolved design. Do not mechanically rewrite unrelated source only to satisfy
this preference. See `docs/guide/SOURCE_STYLE.md` for the human-facing policy and
examples.


## Public contribution and AI-assisted development policy

`CONTRIBUTING.md` is the contributor-facing policy for this repository. Agents
producing, reviewing, or describing contributions MUST preserve its intent.

AI-assisted and fully AI-generated contributions are allowed. Agents MUST NOT
reject, devalue, or impose additional requirements on a contribution solely
because AI produced some or all of it. There is no required human-authored
percentage.

The absence of a human authoring requirement does not reduce the evidence bar.
AI-generated changes remain subject to the same specification audit, scope,
review, validation, repository-state, and publication requirements as other
changes.

For substantive AI-generated work, seek an independent review when practical.
Independent review does not require a human reviewer: a separate agent, model,
session, or clean evaluation context may perform it, provided the review
actually re-examines the diff, relevant authority, assumptions, edge cases, and
validation evidence and can challenge the proposed change. Do not describe an
uncritical continuation of generation as independent review.

When preparing a pull request or other contributor-facing submission in which AI
materially produced or shaped the change, include the brief AI-assistance
disclosure requested by `CONTRIBUTING.md`. Do not require prompt transcripts,
token counts, private conversations, credentials, or model-by-model logs unless
a separate explicit project decision introduces such a requirement.

Never fabricate human authorship or human review. Describe AI-based generation
and review accurately.

## Scoped agent instructions

Before reading, editing, reviewing, or generating files in a repository
subtree, check for an `AGENTS.md` governing that subtree and apply it together
with this repository-root file.

In particular:

- work under `spec/` is additionally governed by `spec/AGENTS.md`;
- work under `src/` is additionally governed by `src/AGENTS.md`.

More specific instructions may add or refine rules for their scope, but they do
not silently discard repository-wide requirements.

## Mandatory pre-implementation audit

Before designing, modifying, or generating a patch for implementation work,
agents MUST audit the repository sources that define the intended behavior and
the constraints of the work. Reading source code alone is not sufficient.

Before implementation:

1. Find and read every applicable `AGENTS.md`, including this repository-root
   file and any more specific file governing code, tests, documentation, or
   specification material involved in the task.

2. Search and inspect the relevant project documentation under `docs/**/*.md`.

3. Search and inspect the relevant normative specification under
   `spec/**/*.md`.

4. Inspect the actual implementation and tests in the current repository
   revision.

The required reasoning order is:

    applicable AGENTS.md
            ↓
    relevant docs/**/*.md
            ↓
    relevant spec/**/*.md
            ↓
    current implementation and tests
            ↓
    derive constraints
            ↓
    design / compare alternatives
            ↓
    explicit user approval when a substantive design choice is required
            ↓
    implementation

Do not design an implementation first and then search the specification for
justification.

### Cost-aware task decomposition

After the required audit and before implementation, assess whether the requested
work is too large, costly, risky, or uncertain to implement safely as one
monolithic change. When coherent independent boundaries exist, prefer a sequence
of smaller implementation slices over one oversized patch.

This assessment is continuous, not a one-time pre-implementation gate. If work
that initially looked bounded becomes materially costly, slow, context-heavy,
failure-prone, or repeatedly requires broadening the patch or reworking the same
surface, the agent MUST stop expanding the current slice and automatically
subdivide the remaining work before continuing whenever independently valid
boundaries exist. Do not wait until an oversized patch, harness, or validation
cycle has already become difficult to reason about.

Automatic decomposition of already-approved implementation work does not require
user approval when it preserves the requested parent outcome, approved
semantics, priorities, and dependency order. The agent should choose the
smallest useful next slice, complete and publish that slice under the ordinary
validation/publication rules, then re-audit current `origin/main` before the next
slice.

Ask the user before decomposition only when the proposed split would itself make
or expose a substantive design choice, alter the requested outcome or priority,
require an independently invalid intermediate state, or leave multiple materially
different dependency orders whose choice has project-level consequences. Cost or
agent difficulty alone is not a reason to return the decomposition decision to
the user.

Signals that decomposition is appropriate include, without being mechanical
thresholds:

- several independently meaningful behaviors, subsystems, or semantic owners
  would change at once;
- the expected diff or validation surface is large enough that failures would be
  difficult to localize;
- one part can establish a prerequisite or invariant that later parts can build
  on independently;
- a monolithic change would create an unnecessarily long validation/publication
  cycle or large exposure to concurrent `origin/main` movement;
- implementation uncertainty is concentrated in separable areas;
- available environment, tool, memory, context, or execution constraints make a
  smaller validated unit materially safer to complete.

Decomposition is an implementation strategy, **not a reduction of the requested
outcome**. If the user asks to implement or close a parent item completely, the
parent remains open until every slice required for that outcome is implemented,
validated, and published. Completing one convenient slice is not evidence that
the parent task is complete.

A good slice SHOULD:

- have one narrow, explainable objective;
- state its prerequisites and the invariants it establishes for later slices;
- be independently reviewable and validatable;
- leave the repository in a coherent, buildable/publishable state under the
  adaptive validation policy;
- avoid temporary observable semantics that will immediately be replaced by a
  later slice;
- minimize overlap with unrelated files and concurrent work;
- make failure or rollback attributable to that slice rather than to a large
  mixed change.

Order slices by real dependency. Prefer foundational representation or internal
mechanism before behavior that depends on it, and behavior before final
cross-slice closure/conformance. Do not create a dependency merely to force a
preferred order when two slices can progress independently.

Do not create a new formal project work item or GitHub sub-issue merely because
an informal task was decomposed. A sub-item deserves independent GitHub tracking
when it has durable project identity: for example, it can be blocked, assigned,
reviewed, validated, or completed independently. Cost-driven micro-decomposition,
diagnostic attempts, launcher revisions, and other execution-only splits remain
inside the parent Issue/checklist, local plan, or publication report. When an
already-formal family uses slice suffixes (for example `Ixxx-A` or `LIBxxx-B`),
preserve that identifier in the owning durable record and GitHub tracking only
when the slice meets this independent-identity criterion.

Before starting each subsequent slice, re-fetch and inspect the current
`origin/main` and re-check the assumptions that slice depends on. A previously
published slice is historical evidence, not permission to assume that unrelated
concurrent changes have not altered the implementation surface.

For an explicitly requested complete implementation in an automated workflow,
agents SHOULD continue through the derived slices until the parent outcome is
closed, unless a genuine normative blocker, environment limitation, failed
validation, publication conflict, or user-requested stopping point prevents
further progress. Do not ask the user to choose among obvious implementation
slices when the dependency order can be derived from the audited repository.

When the workflow requires the user to execute publication launchers manually,
provide one safe publishable slice at a time. After the user reports successful
publication, continue from the new `origin/main` with the next required slice
without reopening design decisions that were already closed, unless current
repository evidence invalidates them.

Do not decompose a change when the semantics or repository invariants require it
to be atomic, when no independently valid intermediate state exists, or when the
coordination/ledger overhead of slicing would exceed the risk it removes. In
that case, keep the change atomic and explain the reason in the implementation
plan/report.

### Search, do not guess the relevant documents

Actively search documentation and specification using the concepts, symbols,
operations, issue identifiers, dependencies, and cross-references relevant to
the task.

Files or search terms named in a task are minimum hints, not an exhaustive
scope. Do not assume that the document directly associated with an
implementation item contains every applicable requirement. Follow relevant
cross-references.

Historical information supplied in a prompt, previous conversation, blocker,
ledger, or earlier implementation report is context only until verified against
the current repository revision.

### Specification and documentation disagreements

Apply the normative authority and document responsibilities defined elsewhere
in this file and in the applicable `spec/AGENTS.md`.

If documentation, specification, tests, and implementation appear to disagree:

- identify the exact files and relevant clauses;
- determine which source owns the behavior;
- report any genuine contradiction;
- do not silently choose the implementation or invent semantics.

Implementation difficulty is not a normative gap. Declare a normative blocker
only when the applicable normative material leaves an observable semantic
choice genuinely unresolved.

### Audit evidence is required

Generic statements such as:

    AGENTS inspected
    docs inspected
    spec inspected

are not sufficient evidence of an audit.

For implementation work, the final report MUST enumerate the files materially
inspected, grouped as:

    AGENTS.md read:
    - ...

    docs read:
    - ...

    spec read:
    - ...

The report MUST also summarize the implementation-relevant constraints derived
from those sources.

This requirement allows later review to distinguish a real
normative/documentation audit from a source-code-only implementation.

### Patch-authoring environments

Lack of a local checkout, Maven, or other build tooling in the agent environment
does not waive this audit when repository contents are available through another
read-capable mechanism.

Such an environment limitation also does not by itself prevent patch authoring
when the required repository contents can be inspected and the generated patch
is intended to be executed and validated later in the user's real checkout.

### Proportional hybrid contribution and publication workflow
<!-- GITHUB002-C HYBRID-CONTRIBUTION-PUBLICATION-CONTRACT -->

Protos uses GitHub Discussions, Issues, the `Protos Development` Project, and
Pull Requests where each mechanism adds value, but it does **not** require every
maintainer/agent-generated patch to pay the cost of a remote PR + CI lifecycle.

The current proportional model is:

- **Discussions** own exploration, questions, early ideas, and pre-decision
  conversation.
- **Issues + Project** own actionable live coordination, assignment, status,
  priority, and roadmap.
- **External contributions** normally use a branch/fork + Pull Request + CI/review
  before merge.
- **Maintainer/agent-generated patches** may use the governed isolated
  direct-to-`main` launcher workflow below after the required adaptive local
  validation passes.
- **Maintainer Pull Requests are optional** and SHOULD be used when their review,
  integration, audit, or collaboration value justifies their operational cost.

This is an intentional scaling rule: collaboration infrastructure should become
stricter as real contributor/concurrency needs appear. Do not impose future-scale
coordination cost on routine current-scale work merely because that machinery may
be useful later.

`GITHUB002-B` temporarily made PR-first mandatory for newly generated internal
launchers. `GITHUB002-D`/PR #153 demonstrated that mechanism successfully, but the
project owner explicitly rejected its routine operational cost at the current
stage. `GITHUB002-C` supersedes that mandatory-PR rule. The experiment remains
historical evidence; it is not the current publication contract.

For the ordinary maintainer/agent direct-to-`main` publication workflow, a
generated launcher MUST:

1. verify that the supplied path belongs to the intended repository and validate
   the configured `origin` coordinate;
2. fetch `origin/main` without changing the caller checkout and record that exact
   execution-time commit as `PUBLICATION_BASE`;
3. create a uniquely named **local-only temporary branch** from exactly
   `PUBLICATION_BASE` and check it out in a temporary `git worktree` outside the
   caller checkout;
4. inspect the files that actually exist in that worktree and evaluate semantic
   preconditions there;
5. materialize only the requested bounded/state-aware delta against those
   execution-time files;
6. derive moving repository metadata from `PUBLICATION_BASE`, preserving unrelated
   execution-time content in shared files;
7. treat unrelated movement since `AUTHORING_BASE` as compatible unless it
   violates a real semantic precondition;
8. abort before publication when a relevant semantic precondition no longer
   holds or the requested transformation overlaps incompatibly with already
   published work;
9. stage only explicit patch-owned paths and run the adaptive local validation
   required by the definitive delta;
10. create the candidate commit entirely inside the isolated worktree;
11. fetch `origin/main` again immediately before publication and require it still
    equals `PUBLICATION_BASE` for this invocation;
12. publish only by a non-force fast-forward push of the exact candidate commit
    to `refs/heads/main`;
13. never rebase/merge/retry onto a newer `main` inside the same invocation; if
    `origin/main` moved during the publication window, abort and let the user
    rerun the same ZIP so it rematerializes from the new execution-time base; and
14. remove the launcher-owned temporary worktree and local branch on success or
    failure without modifying the caller checkout.

The direct-to-`main` path is not permission to bypass validation. Local adaptive
validation is the merge gate for this governed maintainer path. Documentation or
governance-only changes should not start unrelated heavyweight runtime/test
containers merely to imitate an external PR pipeline.

If a maintainer deliberately chooses a Pull Request for a change, the normal PR
rules still apply:

- use `Closes #N` / `Fixes #N` / `Resolves #N` only for a leaf Issue fully
  completed by merge;
- use `Refs #N` for parents/related work that must remain open;
- report the local validation performed;
- let the repository's PR CI run;
- do not call an open PR `PUBLISHED`; publication occurs when the accepted change
  reaches `main`.

`main` is intentionally not PR-only protected at this stage. Reconsider required
PRs/checks/reviews when sustained external contribution volume or maintainer
concurrency makes the additional coordination cost worthwhile.

### Caller-checkout isolation is operational, not a frozen-state assertion

Caller-checkout protection is established by **where the launcher performs
mutating operations**, not by requiring the caller checkout to remain byte-for-byte
unchanged for the whole duration of a potentially long validation run.

A publication launcher MUST scope patch materialization, file writes, staging,
commits, builds, tests, resets/restores (when permitted on launcher-owned state),
and other worktree mutations to its isolated temporary worktree. Against the
caller repository/worktree it may perform only operations needed to inspect Git
metadata, fetch remote refs, create/remove the launcher-owned temporary worktree,
and create/delete the launcher-owned temporary local branch.

A launcher MUST NOT use equality of the caller's `HEAD`, current branch, index,
tracked-file status, or untracked-file status between launcher start and launcher
exit as a publication precondition or postcondition. The caller checkout may be
edited independently while validation runs; observing such a change cannot prove
that the launcher caused it and MUST NOT invalidate an otherwise valid isolated
publication.

Reports SHOULD therefore describe the operational guarantee, for example:

```text
CALLER_WORKTREE_TOUCHED_BY_LAUNCHER: NO
```

rather than claiming that the caller checkout itself stayed globally unchanged
while unrelated tools or the user may have modified it.

For a governed direct-to-`main` launcher, `PUBLISHED` may be printed only after the
non-force fast-forward push of the exact validated candidate to `main` succeeds.
Once that push succeeds, later failure to remove launcher-owned local temporary
state MUST be reported as `CLEANUP_WARNING` and MUST NOT rewrite the already true
publication result into a generic failure.

Before publication, failed validation, relevant semantic-precondition failure,
unexpected launcher-owned state, publication-window movement of `origin/main`, or
failed fast-forward push remains a hard failure. For an optional Pull Request
workflow, opening the PR is not publication and MUST be reported separately.

A launcher may manage only the temporary branch/worktree and patch-owned state it
created itself. An unexpected launcher-owned worktree change, failed validation,
relevant semantic-precondition failure, execution-window movement of
`origin/main`, or failed fast-forward publication is a reason to abort. It is
never permission to repair or rewrite the caller checkout, force-push `main`, or
discard another agent's work.

Temporary publication branches used by the ordinary maintainer direct-to-`main`
launcher are implementation machinery, not project work items. They MUST remain
local, MUST NOT be pushed to `origin`, and MUST be deleted when the launcher
terminates. An optional Pull Request deliberately uses its own remote head branch
under the PR workflow instead; do not conflate that contributor/review branch
with the launcher's local publication machinery.

This isolation changes only patch-publication mechanics. It does not weaken
scope, audit, validation, specification, versioning, license, or explicit user
approval requirements, and it does not authorize a patch to incorporate or
publish changes from the caller checkout.

### Generated patch artifact acceptance gate

A generated patch ZIP and its launcher are executable project work, not merely a
transport wrapper around an intended diff. An agent MUST validate the artifact
itself before presenting it to the user as ready to execute.

Before delivering a generated publication ZIP, the authoring agent MUST, to the
extent the required repository content is available:

1. establish and record the exact `AUTHORING_BASE` used for repository audit and
   artifact acceptance. Do **not** treat that value as the future
   `PUBLICATION_BASE`;
2. run syntax/parse checks for every generated executable helper, including
   `bash -n` for shell launchers and the applicable compile/parse check for any
   Python, Java, Node, Ruby, Perl, or other generated helper;
3. execute every generated **content transformation** against the exact
   `AUTHORING_BASE` contents of each patch-owned file it will modify. A
   hand-written or synthetic fixture may add edge-case coverage, but MUST NOT
   substitute for this exact-current-file transformation test;
4. for every patch-owned file explicitly classified as moving/shared, also test
   **forward compatibility** by applying representative unrelated preceding
   changes before running the transformation. At minimum, when applicable,
   exercise a changed Maven patch version, additional unrelated changelog
   entries, and unrelated status/blocker/ledger edits, and verify that the
   artifact derives from and preserves that newer state rather than requiring
   the authoring snapshot;
5. verify that transformations produce exactly the intended changed-file set and
   satisfy semantic/static postconditions. For shared moving files, verify
   preservation of unrelated content as well as the intended edit;
6. verify the final ZIP/archive structure and integrity, including the expected
   single package root, required files, executable permission bits where
   relevant, and successful archive integrity/CRC inspection;
7. inspect the generated launcher as one whole workflow for its Git-state
   preconditions, isolated-worktree lifecycle, execution-time
   `PUBLICATION_BASE`, dynamic shared-file materialization, explicit staging
   scope, adaptive-validation commands, publication-base stability check,
   publication command, cleanup path, and final report;
8. verify explicitly that the launcher does **not** require
   `PUBLICATION_BASE == AUTHORING_BASE` and does not reject benign main movement
   merely because an old whole-file hash, line number, version, changelog header,
   or unrelated ledger text changed;
9. when the authoring environment provides a disposable Git checkout or can
   construct a faithful local Git harness without changing project semantics,
   execute an end-to-end launcher dry run with a remote whose `main` has advanced
   benignly beyond `AUTHORING_BASE`; require the artifact to materialize and
   reach the publication gate successfully from that newer
   `PUBLICATION_BASE`. Keep the actual push disabled or redirect it to the
   disposable local remote; and
10. never report an artifact-level check as `PASS` unless that exact check was
    actually executed successfully.

Generated-launcher acceptance MUST also exercise caller isolation as an
operational property. When an end-to-end disposable Git harness is available,
the harness SHOULD make an unrelated caller-worktree change while the launcher is
operating and verify that the isolated patch still reaches its publication gate,
that the unrelated caller change is preserved, and that the launcher performs no
file/index/commit mutation in the caller checkout.

Acceptance MUST verify that a successful disposable push cannot subsequently be
reported as a failed publication merely because caller state differs from the
launcher's initial observation. Post-push cleanup failure may be simulated to
verify warning/reporting behavior, but it must not turn an already successful
push into a generic `ERROR: caller checkout state changed`.

A generated artifact that fails any authoring-time acceptance check MUST be fixed
and re-tested before it is shown to the user. Do not intentionally use the
user's real checkout as the first test of a generated parser, patch hunk,
replacement anchor, helper script, ZIP layout, Git worktree flow, or similar
deterministic launcher machinery.

If `origin/main` advances while an agent is still authoring a patch, that fact
alone does not reset the work. The agent may continue preparing the
forward-compatible artifact from its audited `AUTHORING_BASE`; before delivery,
it should use the newest repository content available to test compatibility of
the semantic transformation where practical. Re-audit/regeneration is required
only when new evidence changes the slice's relevant semantics/preconditions or
the artifact cannot forward-materialize safely.

If the authoring environment cannot execute a required acceptance check, report
that specific limitation accurately. Lack of one unavailable check does not
justify inventing `PASS`, but agents should still perform every exact-content,
forward-compatibility, syntax, archive and static workflow check that their
available tools permit.

This acceptance gate is distinct from project behavioral validation. Maven focal
or full-suite tests are still selected from the definitive execution-time
repository delta by the adaptive validation matrix. Artifact acceptance exists
to establish that the generated launcher can correctly materialize and
orchestrate that delta across normal intervening publications before the user's
checkout becomes its first real execution environment.

### Environment and toolchain discipline for generated patches

Generated patches and their launchers MUST run against the repository's declared
development environment rather than assuming newer host tools happen to exist.
Inspect the applicable environment and build declarations such as
`.devcontainer/`, `pom.xml`, `Makefile`, and repository scripts before choosing
helper-tool requirements.

Patch materialization SHOULD minimize incidental tool dependencies, but
**reliability against `PUBLICATION_BASE` is more important than forcing every
change through `git apply`**. Use the simplest repository-declared or
baseline-compatible mechanism that can express the requested transformation
safely. For stable files an ordinary unified diff is often ideal. For moving
shared files such as `pom.xml`, `CHANGELOG.md`, and canonical ledgers, a bounded
state-aware edit that reads and validates the current worktree content is
preferred when that avoids stale-context failures. Any helper runtime must still
satisfy the version/toolchain checks below; do not add incidental dependencies
merely for convenience.

Never assume an unversioned command name implies a modern runtime. In particular,
`python3` may denote an older system Python. If a helper language is genuinely
required, the launcher MUST, before modifying repository state:

1. locate the intended executable explicitly;
2. inspect its actual version;
3. verify that version satisfies the helper's declared minimum; and
4. use only syntax and APIs compatible with that verified version.

Apply the same principle to Java, Maven, Node, compilers, formatters, and other
tools: use the repository-declared toolchain and validate required capabilities
before relying on them. Do not install, upgrade, replace, relink, or reconfigure
system tools, packages, `PATH`, shell profiles, the devcontainer, or the project
toolchain merely to make a generated patch helper run unless changing that
toolchain is itself the explicit task.

If a genuinely required tool or compatible version is unavailable, abort before
repository modification and report `ENVIRONMENT_LIMITATION` with the required
and observed tool/version information. Prefer rewriting the patch launcher to
use already-declared compatible tools when that can be done without changing the
requested repository behavior.

### Truncated repository reads

Truncation of a single repository read does not by itself make a source
inaccessible and does not justify declaring an environment limitation.

When a required repository file is too large to retrieve in one response,
agents MUST attempt to inspect it exhaustively through segmented retrieval
before concluding that the source cannot be read.

Depending on the repository access mechanism, this may include:

- reading successive ranges or chunks of the same file;
- locating the complete heading or section structure and retrieving sections
  individually;
- using exact heading, section identifier, clause identifier, or distinctive
  text searches to recover bounded portions of the file;
- following pagination, continuation, cursor, or range mechanisms provided by
  the repository tool;
- combining multiple partial reads while keeping track of which portions have
  and have not been inspected.

Concept searches alone are not sufficient for an exhaustive audit of a
normative document, because an agent cannot assume in advance that it knows
every relevant concept. When the whole normative document is required, first
establish its complete structure as far as the available repository interface
permits, then inspect every section that may contain normative material.

Agents SHOULD maintain an internal coverage checklist for large normative
documents so that omitted or unread sections are detectable before design
begins.

An environment limitation based on repository-read restrictions may be
declared only after reasonable segmented-retrieval attempts have failed to
recover required content. The report MUST then identify:

- the exact file involved;
- the sections or ranges successfully inspected;
- the sections or ranges that remain inaccessible;
- the retrieval methods attempted;
- the concrete limitation that prevents further retrieval.

A response-size limit, truncation of one tool result, lack of a local checkout,
or lack of network access is not by itself sufficient if the required content
can still be recovered through another available read-capable path.

Do not declare an implementation item blocked merely because the patch-authoring
environment lacks a local checkout or build toolchain.

### Remote branches

Agents MUST NOT create or push temporary, staging, validation, audit, or
agent-specific remote branches unless the task explicitly requires a remote
branch or pull request.

Temporary branches used for patch construction, rebasing, or validation SHOULD
remain local.

For the standard patch-publication workflow, publish the validated commit
directly to `main` only after synchronizing with the current `origin/main` and
rerunning the validation required by the adaptive test/validation rules below.

Do not leave temporary remote branches behind after successful publication.

## Unresolved language-design questions

When a request raises an unresolved question about what Protos should mean,
treat it as language-design work before treating it as implementation work.

Apply the specification-design process in `spec/AGENTS.md`: research relevant
prior art, compare alternatives and trade-offs, test them against the Protos
design philosophy, attempt to falsify candidate designs, and recommend the best
Protos semantics before implementation begins.

Do not bypass this process merely because the original request was phrased as
"implement X".


### GitHub-native project coordination

GitHub is the live coordination layer for actionable Protos project work. This
coordination role is deliberately separate from language/design authority and
from durable repository evidence.

The authority split is:

- normative Protos semantics remain owned by the applicable files under `spec/`;
- ratified `Dxxx` and `PLATxxx` decisions remain durable repository records and
  are not redefined by an Issue, Discussion, reaction, vote, or Project field;
- `docs/project/IMPLEMENTATION_BLOCKERS.md` remains the durable repository ledger
  for `Bxxx` normative unblock conditions;
- GitHub Discussions is the preferred public surface for questions, ideas,
  investigation, and pre-decision design discussion;
- GitHub Issues is the canonical live coordination surface for actionable
  project work such as `Ixxx`, `CLIxxx`, `TOOLxxx`, `PERFxxx`, `DISTxxx`,
  `DOCxxx`, `LIBxxx`, `AUDxxx`, `LMxxx`, and comparable work families;
- the `Protos Development` GitHub Project is the canonical live scheduling and
  prioritization view once an Issue is present there; and
- merged/published repository state, owning project records, tests, changelog
  entries, and Git history retain durable implementation/closure evidence.

No GitHub surface has design-approval authority. A Discussion reaching consensus,
an Issue being closed, a Project item moving to `Done`, a reaction count, or a PR
being merged does not satisfy the explicit project-owner approval gate for a
substantive design decision. Apply the design-authority rules above exactly as
before.

<!-- GITHUB001-F LEGACY-LIVE-LEDGER-RETIREMENT -->
`docs/project/OPEN_TASKS.md` is a retired historical backlog snapshot.
`docs/project/IMPLEMENTATION_STATUS.md` is a durable implementation registry and
closure-evidence ledger. Neither file is a live scheduling/status source.

Agents MUST NOT add new actionable work to `OPEN_TASKS.md`, update it to mirror
GitHub, or use it to decide what should run next. Agents MUST NOT update
`IMPLEMENTATION_STATUS.md` merely to mirror `OPEN`, `READY`, `IN_PROGRESS`,
`BLOCKED`, assignee, priority, or roadmap changes. New actionable work and all
live scheduling/status belong to GitHub Issues and the `Protos Development`
Project.

`IMPLEMENTATION_STATUS.md` may still preserve or add durable historical/closure
evidence when a repository publication genuinely needs that registry function,
but a row there never reserves work, releases work, blocks work operationally,
or overrides the owning Issue/Project's live coordination state. Preserve both
legacy files as historical evidence; do not bulk-delete their retained content.

Before starting actionable implementation/project work, agents MUST:

1. inspect the current GitHub Issue for the work item when one exists;
2. inspect current `origin/main`, the owning durable project/design records, and
   every applicable blocker/specification source;
3. reconcile stale Issue claims against repository fact before relying on them;
   and
4. preserve the authority split above when updating either side.

GitHub live state is coordination data, not proof that code exists. If an Issue
or Project field conflicts with current published repository state, use the
repository/specification to establish what actually exists and then reconcile
GitHub; do not rewrite durable semantics or implementation evidence merely to
match stale coordination metadata.

When a bounded implementation slice is successfully published, the publication
commit MUST still record every durable artifact required by its owning work item
(for example implementation version/changelog, owning project record, tests,
blocker transition, or normative decision evidence where applicable). The patch
launcher itself MUST NOT require GitHub API credentials and MUST NOT create,
close, relabel, assign, or move Issues/Project items. Only after publication is
confirmed as successful may the coordinating agent update the corresponding
GitHub live state. If the agent cannot perform that GitHub write, it must report
the exact coordination update still required rather than pretending it happened.

The standard isolated direct-to-`main` publication workflow remains valid. Moving
project coordination to GitHub does not by itself require every agent-generated
change to use a pull request. Use a PR when the contribution workflow, review
policy, or explicit user request requires one; otherwise follow the publication
rules in this file.

Live coordination state such as assignee, `In progress`, `Blocked`, or a Project
column is advisory coordination, not a repository lock. Multiple agents must
still re-fetch `origin/main`, inspect overlapping work, and avoid assuming that a
GitHub assignment grants exclusive ownership of mutable files or semantics.

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

Formal project identifiers retain their existing family meanings. Identifier
syntax alone does not establish authority: discover existing identifiers and
family semantics from current repository records and migrated GitHub history,
not from chat memory or a token that merely looks like `I123`.

New actionable formal work SHOULD have a GitHub Issue when it is allocated. An
operational identifier MUST NOT exist only in a prompt/chat. When the work also
has an owning durable repository record, persist the identifier there no later
than the first repository publication that materially establishes the work.

<!-- GITHUB001-A2 FAMILY-IDENTIFIER-POLICY -->
##### Family labels

Every GitHub Issue that represents a formal actionable Protos work item MUST
carry exactly one stable family-classification label named `family:<FAMILY>`,
where `<FAMILY>` is the identifier prefix. Examples include `family:I`,
`family:LIB`, `family:TOOL`, `family:CLI`, `family:PERF`, `family:DOC`,
`family:DIST`, `family:AUD`, `family:LM`, and `family:GITHUB`.

A formal sub-issue keeps the same family label as its formal identifier. The
family label classifies durable project ownership only. Do not encode live
status, priority, roadmap position, assignee, blocking state, or parent/child
structure in family labels; those belong to the Issue/Project/native hierarchy.
Ordinary community Issues that do not have a formal Protos work identifier do not
need a family label.

`Dxxx`, `PLATxxx`, and `Bxxx` remain repository-owned decision/blocker families
rather than ordinary actionable Issue families. When an actionable Issue consumes
or is blocked by one of those records, label the Issue by its owning actionable
family and link the applicable `Dxxx`/`PLATxxx`/`Bxxx` authority instead of
reclassifying the decision/blocker as project work.

GitHub's numeric Issue identifier and a Protos formal identifier are independent
namespaces. For example, GitHub Issue `#73` may own `I031`; agents MUST NOT try to
make those numbers coincide.

##### Collision-safe formal identifier allocation

Top-level formal family numbers are monotonic allocation identifiers, not a
reusable pool. Closed, cancelled, superseded, retrospectively imported, or
otherwise previously allocated identifiers remain consumed. Do not fill an old
gap merely because its number appears unused in the current active-work view.
Slice suffixes such as `I031-A` or `TOOL003-B2` do not allocate another top-level
family number.

Before allocating a new top-level identifier, the coordinating agent MUST:

1. verify that the work genuinely belongs to that formal family and deserves a
   new durable work identity rather than a Discussion, ordinary Issue, existing
   parent, or mechanical slice;
2. search current durable repository records plus all relevant GitHub Issues,
   including closed/historical Issues, for allocated identifiers in that family;
3. choose the next number after the greatest already allocated top-level number;
4. create the GitHub Issue immediately with a title beginning
   `<IDENTIFIER> — ...` and apply the matching `family:<FAMILY>` label; and
5. re-search the candidate identifier after creation before treating the
   allocation as confirmed.

The post-create re-search is the concurrency gate. If two agents race and create
the same previously-free identifier, the Issue with the lower GitHub Issue number
keeps that Protos identifier. Every later colliding Issue MUST re-scan repository
and GitHub state, choose the then-next family number, rename itself, and repeat
the uniqueness check until it owns a unique identifier. If the candidate already
belongs to a durable repository record, that repository allocation wins and the
new Issue must be renumbered regardless of GitHub Issue ordering.

Do not publish a new durable repository record, changelog entry, source reference,
or implementation under a newly allocated identifier until that identifier has
passed the post-create uniqueness check. If GitHub Issue creation or verification
is unavailable, report the coordination limitation rather than reserving a new
formal identifier only in chat or a local patch.

Historical migration is different from new allocation: an already-established
repository identifier keeps its historical number. Backfill creates the
retrospective Issue with that same identifier and never renumbers historical
project work merely to obtain a contiguous sequence or to match GitHub Issue
numbers.

Standard Library work uses `LIBxxx`; documentation initiatives use `DOCxxx`;
official bundled tools use `TOOLxxx`; performance work uses `PERFxxx`; Language
Maturity uses `LMxxx`; and the other existing families retain their documented
boundaries. Moving lifecycle coordination to GitHub does not change those family
semantics and MUST NOT be used to smuggle language-design authority into an
implementation/project family.

Parent Issues remain open until the repository-defined parent outcome is
actually complete. Use a sub-issue only for a durable independently meaningful
unit. Temporary patch versions, diagnostics, launcher attempts, and cost-driven
micro-slices are not separate project work items merely because they were useful
to an agent while executing the work.

Retrospective GitHub backfill MUST identify itself as retrospective. GitHub
creation/closure timestamps are not historical project dates. Preserve original
closure commits, versions, durable records, and Git history where available; do
not fabricate unavailable evidence merely to make an old Issue look complete.

### Documentation work

Substantial documentation initiatives with an independently meaningful project
lifecycle use the `DOCxxx` family.

`DOCxxx` is for documentation whose primary deliverable is public or project
explanation, learning material, navigation, conceptual guidance, or another
maintained documentation surface that is useful to track independently from an
implementation item.

Do not use `DOCxxx` merely because a change edits Markdown. Documentation that is
part of closing an `Ixxx`, `LIBxxx`, `TOOLxxx`, `CLIxxx`, `LMxxx`, `PERFxxx`, or
other already-owned work item remains part of that owning family.

Keep these boundaries explicit:

- `spec/` remains the normative authority for Protos language semantics;
  `DOCxxx` MUST NOT create, complete, or reinterpret missing normative behavior;
- implementation status remains owned by the applicable implementation family;
- executable conformance/tutorial dogfooding whose primary purpose is maturity
  coverage remains `LMxxx`;
- a future bundled documentation generator/tool is `TOOLxxx`, not `DOCxxx`;
  `DOCxxx` owns documentation content/lifecycle, not toolchain orchestration.

When documentation discovers a genuine normative ambiguity, record the applicable
normative blocker and mark only the affected `DOCxxx` slice blocked. Do not block
independent documentation work that can proceed from already-defined semantics.

New independently tracked documentation work uses the next unused `DOCxxx`
identifier, creates/uses its GitHub Issue for live coordination, and records it
in an owning durable project record when the first repository publication
materially establishes the work. Operational `DOCxxx` identifiers MUST NOT exist
only in prompts or chat history.

The first tracked documentation initiative is `DOC001 — Protos Programming
Documentation`, owned by
`docs/project/DOC001_PROGRAMMING_DOCUMENTATION.md`.

### Toolchain tool work

Official toolchain-bundled developer tools use the `TOOLxxx` family once they
are promoted from exploratory architecture to tracked implementation work.
`TOOLxxx` tracks the lifecycle and policy surface of the tool itself; it is
independent of the public command spelling used to invoke that tool.

The initial assignments are:

- `TOOL001` — Package Tool;
- `TOOL002` — Test Tool.

Future official bundled tools such as benchmark, formatter, linter,
documentation, coverage, or profiling tools should allocate the next unused
`TOOLxxx` identifier when they become concrete tracked work. Do not create a new
project-work family solely because a new tool has a distinct public command.

Keep the following boundaries explicit:

- `CLIxxx` owns independently meaningful driver, terminal, REPL, command
  selection/dispatch, and general command-line UX mechanics. A bundled tool does
  not become `CLIxxx` work merely because users reach it through `protos`.
- `TOOLxxx` owns bundled-tool policy and tool-specific orchestration implemented
  primarily as ordinary Protos code where the selected architecture permits it.
- `PERFxxx` owns performance engineering of the Protos project itself. A future
  user-facing benchmark or profiling tool is still `TOOLxxx`; `PERFxxx` may use
  such a tool as evidence without becoming that tool.
- `LIBxxx` owns distributable Standard Library API. Bundled tool implementation
  modules are not Standard Library merely because they ship with the toolchain.
- a future third-party plugin/extension mechanism is a separate design problem;
  official bundled tools are not plugins by default.

`TOOL001` was introduced after Package Tool implementation had already begun.
Historical package-tool labels such as the bootstrap slice, Filesystem Slice 2A,
Filesystem Slice 2B / B006, and manifest Slice 3 sub-slices remain valid
historical names. The TOOL001 project record maps them into the canonical current
lifecycle without rewriting commits, changelog entries, or prior evidence.

When promoting a new bundled tool, create/use the `TOOLxxx` GitHub Issue for
live coordination and record the parent plus any durable formalized slices in an
owning repository project record. Exploratory tool architecture alone does not
reserve an identifier.

### Performance work

Performance work uses the `PERFxxx` family. It records non-normative benchmark
suites, profiling and performance investigations, optimization work, and
performance-regression protection over behavior that is already defined by the
applicable specification and implementation owners.

`PERFxxx` work MUST NOT be used to redefine, relax, or bypass observable Protos
semantics, correctness requirements, conformance requirements, capability
boundaries, or lifecycle guarantees for the sake of a benchmark result. If a
performance improvement requires an observable semantic change, route that
change through the applicable specification/design process and implementation
family before treating the resulting implementation as performance work.

New Performance work uses the next unused `PERFxxx` identifier and creates/uses
its GitHub Issue for live coordination when formally introduced. Benchmark
workloads, result files, test names, commit messages, or incidental prose do not
become tracked work items merely because they contain a PERF-shaped token; the
Issue/Project coordination layer owns live lifecycle state while durable
repository records retain evidence.

Performance changes that modify executable implementation source under `src/`
or distributable Protos library source under `protos/lib/` remain subject to the
normal implementation versioning, changelog, validation, and publication rules.

## Implementation blockers

`docs/project/IMPLEMENTATION_BLOCKERS.md` is the repository-wide ledger for
implementation work that cannot proceed without unresolved normative semantics.
It records implementation state only; it is not part of the Protos specification.

When implementation work reaches a semantic dependency that is not normatively
closed or otherwise precise enough to implement without guessing:

- do not invent the missing semantics;
- do not block unrelated implementation work;
- add or update a blocker in `docs/project/IMPLEMENTATION_BLOCKERS.md`;
- give the blocker a stable identifier;
- identify the blocked implementation area;
- state the exact normative dependency;
- identify the specification documents or sections that own the decision;
- state an objective, verifiable unblock condition;
- record what implementation work may safely continue independently.

A blocker must be based on a normative condition, not on a predicted revision,
commit SHA, implementation strategy, agent-specific memory, or expected wording
of a future specification change.

Before beginning implementation work, inspect
`docs/project/IMPLEMENTATION_BLOCKERS.md`. Re-check every relevant `BLOCKED` or `READY`
entry against the current normative specification on the current `main` branch.
Never rely on the specification state that existed when the blocker was
recorded.

If a `BLOCKED` entry's unblock condition is now satisfied, mark it `READY`,
re-audit the affected semantics against the current specification, and resume
the work when it is within the current task or when no higher-priority
independent work takes precedence.

When the blocked implementation is completed, superseded, or no longer
applicable, mark the entry `CLOSED` and briefly record why. Do not delete closed
entries merely to hide project history.

Canonical language specification

The normative core language definition is maintained in:

- `spec/PROTOS_LANGUAGE_SPEC.md`
- `spec/PROTOS_GRAMMAR.md`

`spec/runtime/ABSTRACT_RUNTIME.md` is an informative execution model and is not
part of the normative authority set.

Normative domain models supplement those core documents for semantically substantial standard subsystems. The current normative domain model is:

- `spec/semantics/OBJECT_MODEL.md` — primary normative owner of migrated object-model semantics.
- `spec/semantics/EXECUTION_AND_CONTROL.md` — primary normative owner of execution contexts, lookup/evaluation, and iteration/control semantics.
- `spec/semantics/CALLABLES.md` — primary normative owner of Closure value/capture and callable/invocation semantics; syntax, trailing-closure attachment, operator precedence, and mandatory desugaring remain owned by `spec/PROTOS_GRAMMAR.md`.
- `spec/semantics/MODULES.md` — primary normative owner of migrated module semantics.
- `spec/semantics/ERRORS.md` — primary normative owner of migrated Error semantics.
- `spec/semantics/VALUES_AND_COLLECTIONS.md` — primary normative owner of value-family, equality/identity, indexing, and collection semantics.

- `spec/io/IO_CORE.md` — cross-cutting I/O capability, commitment, lifecycle, and wrapper semantics.
- `spec/io/BYTE_IO.md` — byte-I/O protocol semantics.
- `spec/io/TEXT_IO.md` — encoding and text-I/O semantics.
- `spec/io/FILESYSTEM.md` — filesystem authority, File, Path, and file-URL semantics.
- `spec/io/PROCESS_IO.md` — Process I/O bootstrap, environment/arguments, standard streams, and Process-local I/O authority.

- `spec/concurrency/FUTURES_AND_TASKS.md` — primary normative owner of migrated Core Future/task semantics.
- `spec/concurrency/ACTORS.md` — primary normative owner of migrated Core Actor semantics.
- `spec/concurrency/PARALLEL_EXECUTION.md` — primary normative owner of migrated Core isolated-parallel (P) semantics.
- `spec/concurrency/DISTRIBUTED_RUNTIME.md` — primary normative owner of migrated Process/Node/Cluster/Group/distributed-runtime semantics. Unresolved concurrency design work is non-normative and lives in `docs/design/CONCURRENCY_DESIGN.md`.

Treat the canonical core documents together with applicable normative domain models as the source of truth for observable language/standard semantics in their respective domains.

Concurrency modularization is complete for normative material. Historical and
unresolved concurrency design notes live outside the normative specification in
`docs/design/CONCURRENCY_DESIGN.md`.

Before implementing or modifying syntax, parsing, object semantics, invocation, lookup, control flow, errors, concurrency, built-in protocols, or other observable language behavior, inspect the relevant specification sections first.

The documents have distinct responsibilities:

- `PROTOS_LANGUAGE_SPEC.md` defines the language model and observable semantics.
- `PROTOS_GRAMMAR.md` defines lexical structure, syntax, precedence, parsing rules, mandatory desugarings, and the canonical semantic AST.
- `runtime/ABSTRACT_RUNTIME.md` is a non-normative abstract execution/pseudocode model constrained by the normative owners.
- `spec/io/IO_CORE.md` owns observable I/O-domain semantics and should be referenced rather than duplicated when another document only needs an I/O rule.

Do not treat runtime pseudocode as permission to change language semantics.

If the documents appear inconsistent, stop and report the inconsistency instead of choosing one interpretation silently.

Specification changes

Do not change the language design merely because a different implementation would be easier.

A change to observable language behavior is a specification change, not an implementation detail.

Examples include:

- syntax or precedence
- name lookup
- slot creation or assignment
- delegation
- "this", "context", or "super"
- closure capture
- invocation or argument binding
- equality or identity
- object construction
- error propagation
- Future behavior
- concurrency semantics
- built-in protocol behavior

Do not make such changes unless the user explicitly requests or approves the language-design change.

When an implementation task exposes an unspecified semantic case, report it clearly and wait for a decision.

When an approved semantic change is made, update all affected canonical specification documents in the same change so they remain mutually consistent.

Semantic integrity

Preserve the fundamental Protos design principles.

In particular:

- Everything is an object.
- There are no classes.
- "Object" is the unique root of delegation.
- Every other object has exactly one immutable delegation parent.
- Reads may delegate; writes never delegate.
- ":" creates a slot.
- "=" modifies an existing slot.
- Missing lookup is an error, not "null".
- "null" is the only absence value.
- Execution contexts are objects.
- Object slots are not lexical variables.
- Closures capture genuine lexical execution contexts by reference.
- Methods dynamically receive their receiver through "this".
- "super" is lookup syntax, not an ordinary value.
- Closures are the single executable value kind.
- Parentheses perform polymorphic invocation.
- Evaluation is left-to-right except where explicitly specified as lazy.
- Language mechanisms should use ordinary object protocols where practical rather than parallel special mechanisms.

Do not introduce Java, JavaScript, Python, Smalltalk, or other host/source-language semantics merely because they are familiar.

Implementation architecture

Keep language semantics independent from the implementation framework.

### Core bootstrap boundary

Bootstrap only the irreducible host machinery required to execute Protos.
Do not grow a parallel hardcoded standard library in Java when standard objects
or behavior can be constructed or installed faithfully through ordinary Protos
mechanisms.

The detailed non-normative bootstrap architecture is documented in
`docs/project/CORE_BOOTSTRAP_ARCHITECTURE.md`. Source for distributable Core
behavior that can be expressed in Protos belongs under `protos/lib/core/`.
Host-native primitives should be exposed through the ordinary object/protocol
model wherever that model can represent them.

Temporary Java-side bootstrap scaffolding must be identified as temporary and
must not be extended into additional standard families merely for convenience.


Prefer a pipeline with clear boundaries:

source
  ↓
lexer
  ↓
tokens
  ↓
parser
  ↓
semantic AST
  ↓
Truffle lowering / execution
  ↓
runtime

The parser should produce a semantic representation of Protos, not directly encode incidental Truffle implementation details.

Keep the semantic AST conceptually separate from Truffle execution nodes.

Runtime objects and operations should represent Protos semantics rather than expose Java implementation details.

Host-language implementation details must not become observable language behavior unless explicitly defined by the specification.

Parser

Use the grammar document as the authority for accepted syntax.

Do not accept additional syntax simply because it is easy for the parser to recognize.

Do not silently reject syntax defined by the grammar because it is inconvenient to implement.

Preserve significant newlines according to the grammar. Protos does not use JavaScript-style Automatic Semicolon Insertion.

Operator precedence, associativity, custom operators, contextual "...", trailing closures, indexed access, and assignment must follow the grammar rather than host-language conventions.

When possible, keep parsing and semantic validation separate. A construct that is syntactically valid but semantically invalid should not require grammar distortion merely to reject it earlier.

Runtime

Implement observable behavior from the applicable normative specification owners, not from the informative runtime pseudocode or assumptions about Java objects.

Do not expose Java "null" as Protos "null".

Do not use Java object identity directly as the definition of Protos "===" where the specification defines semantic value identity.

Do not use Java exceptions as an implicit substitute for the Protos error/condition model at language boundaries. Internal Java exceptions may be implementation mechanisms, but they must be translated into the specified Protos behavior.

Do not allow Java collection, numeric, string, threading, or equality semantics to leak into Protos when they differ from the specification.

Implementation optimizations are allowed only when they preserve observable semantics.

Truffle and GraalVM

Treat Truffle as the implementation platform, not as the language definition.

Prefer standard Truffle mechanisms for AST specialization, frames, instrumentation, interop, and optimization when they preserve Protos semantics.

Do not distort the language model solely to obtain an optimization.

Keep Truffle-specific behavior localized where practical so that semantic code remains understandable independently of the framework.

Avoid premature optimization. Establish correct semantics and useful tests before adding specialized execution paths.

When an optimization is introduced, preserve a clear generic semantic path against which specialized behavior can be understood and tested.

Change scope

Keep changes scoped to the requested task.

Do not combine feature work with unrelated refactors, formatting, renaming, dependency upgrades, architecture changes, or cleanup.

Report relevant unrelated problems instead of modifying them unless they block the requested work.

Do not silently introduce a new architectural or semantic pattern when an established one already covers the need.

Before implementing a feature, inspect nearby code and comparable functionality and reuse established abstractions when appropriate.

Compatibility

During language version "0.1", the design may evolve, but implementation changes must still follow the current specification.

Do not preserve accidental implementation behavior when it contradicts the specification.

Conversely, do not introduce a breaking semantic change merely to simplify existing code.

If existing implementation behavior and the current specification disagree, the specification wins unless the user explicitly decides otherwise.

Clearly report compatibility consequences when changing already implemented behavior.

Test execution

Select test scope from the actual changed-file delta and the behavioral impact of
that delta. Tests are evidence for executable behavior; they are not a mandatory
ritual for every repository edit.

For a publication-capable patch or implementation change, classify the definitive
delta after synchronizing with the current `origin/main`:

- **Executable-impact changes** include production implementation under
  `src/main/**`, distributable Protos source under `protos/lib/**`, and build,
  generation, packaging, or runtime configuration whose change can alter the
  compiled or distributed program. Run the focused tests that exercise the
  changed behavior when such tests exist, then run the complete Maven test suite
  before publication.
- **Test-impact changes** under `src/test/**`, `protos/tests/**`, or equivalent
  executable test infrastructure require the affected/focused tests and, for a
  publication change, the complete Maven test suite unless the changed test
  surface is intentionally outside Maven and an equivalent complete project
  validation is documented.
- **Specification-only changes** under `spec/**` do not run Maven tests by
  default. Run the applicable specification governance, consistency, changelog,
  and static checks. If a specific executable guard consumes the modified
  specification artifact, run that focused guard; a full suite is not required
  merely because normative text changed.
- **Documentation/governance-only changes** such as `docs/**`, `AGENTS.md`,
  `CHANGELOG.md`, and project ledgers do not run Maven tests by default. Run the
  applicable documentation, status, governance, formatting, link/integrity, or
  other static checks. If an executable guard specifically consumes a modified
  document or ledger, run that focused guard only unless the delta also has
  executable impact.

### Prefer Protos-level tests for observable language behavior

When behavior can be expressed and observed by executing Protos source, prefer a
Protos-language test over a Java test.

Tests whose primary purpose is to validate observable Protos language semantics,
regressions, conformance, or user-visible standard-library behavior SHOULD
normally be written as executable Protos source under `protos/tests/**` or the
applicable Protos-level test harness. Do not default to JUnit merely because the
implementation under test is written in Java or because a Java test is easier to
write.

A Java test alone SHOULD NOT be treated as sufficient coverage for observable
Protos behavior when the same behavior can reasonably be exercised through
Protos source. In that case, add or update the Protos-level test; a focused Java
test may additionally be kept when it provides useful implementation-level
coverage or diagnostics.

Java tests under `src/test/**` are preferred when the subject being tested is
specifically Java-side implementation behavior rather than Protos language
behavior, including internal Java APIs, representations, invariants, parser or
lowering machinery, Truffle/JVM integration, host interop, bootstrap code, and
test-harness infrastructure that is not meaningfully testable from Protos.

Do not contort a Protos test to reach a purely internal Java contract. The
distinction is semantic: test the language as Protos when the contract is visible
to Protos programs, and test Java as Java when the contract belongs to the
implementation itself.
- **Mixed changes** use the strongest applicable validation class. Adding docs,
  specification text, changelog entries, or status updates to an executable
  change never weakens the executable-change test requirements.

Do not classify only by file extension. For example, `pom.xml`, code generators,
packaging scripts, or runtime configuration may have executable impact even
though they are not Java or Protos source. Conversely, a Markdown-only design
clarification does not gain executable impact merely because it describes code.

Patch launchers SHOULD derive and print their validation class from the actual
post-apply delta, for example:

    VALIDATION_CLASS: EXECUTABLE_IMPACT
    FOCAL_TESTS: PASS
    FULL_TEST_SUITE: PASS

or:

    VALIDATION_CLASS: DOCUMENTATION_ONLY
    FOCAL_TESTS: SKIPPED (not applicable)
    FULL_TEST_SUITE: SKIPPED (documentation-only change)

A user request for a publication-capable automated patch/launcher authorizes that
launcher, when the user executes it, to run the tests required by this matrix and
to publish only after those required validations pass. Do not ask for a second
test confirmation solely because the matrix requires focal or full-suite tests.
Outside such an authorized publication workflow, do not start expensive test
runs automatically; follow the user's requested validation scope.

Do not repeatedly rerun failing tests without first understanding and changing
the likely cause.

Static verification

For source-code changes, run the narrowest applicable formatter check, linter,
compiler check, or static-analysis command needed to verify the modified scope,
unless the check is unavailable, would be expensive, or would require starting
a long-running process.

Prefer dedicated validation/check modes over commands that rewrite files or
perform broader build phases. Static verification MUST NOT implicitly run tests
when tests were not explicitly requested. Formatting tools must not introduce
unrelated formatting changes.

If an applicable verification command would also execute tests, use a non-test
alternative or report that the check was not run.

New or modified code must not introduce compiler, static-analysis, lint, or
formatting diagnostics that indicate correctness, type-safety, maintainability,
or project-style problems in the affected scope.

Do not claim a source change is clean if the applicable static checks were not
run. Report which checks were run, which were not run, and why.

A clean build achieved only by suppressing applicable warnings is not the same
as clean code. Do not add suppressions merely to make tooling pass; use narrowly
scoped suppressions only when the warning is genuinely inapplicable and the
reason is defensible.

Testing philosophy

Tests should validate Protos semantics rather than implementation accidents.

Prefer small semantic tests that demonstrate observable language behavior.

When fixing a semantic bug, add or update a focused regression test when tests are part of the requested work.

Where useful, test the same semantic rule through multiple execution paths, especially when Truffle specialization or optimized nodes could diverge from generic behavior.

Do not weaken a test merely to make an incorrect implementation pass.

If a test contradicts the current specification, report the contradiction instead of treating the test as authoritative.

Operational boundaries

Never commit or push changes unless the user explicitly requests it.

Do not create releases, tags, branches, or pull requests unless explicitly requested.

Do not modify repository history.

Before performing a potentially expensive or long-running operation, explain the expected cost or duration and ask the user for confirmation.

Do not automatically start long-running language servers, development servers, REPL sessions, benchmarks, profiling runs, or watch processes.

Dependencies

Avoid adding dependencies when the functionality is small and reasonably implementable within the project.

Before adding a dependency, check whether the JDK, GraalVM/Truffle platform, or an existing project dependency already provides the required functionality.

Do not upgrade Java, GraalVM, Truffle, build tooling, parser libraries, or other foundational dependencies as part of unrelated work.

Keep GraalVM and Truffle versions deliberately pinned and compatible.

Dependency changes that affect the language implementation architecture should be explained before they are made.

Source file size

Keep hand-written source files focused and reasonably small.

When a source file reaches approximately 600 lines, assess whether it contains separable responsibilities.

Do not normally let a hand-written source file exceed 800 lines; split it into cohesive components before adding substantial new code.

These thresholds are guidelines rather than mechanical limits.

Split by semantic or architectural responsibility, not into arbitrary numbered fragments.

Existing oversized files do not have to be split for a small unrelated change.

Generated files, vendored code, lock files, fixtures, and primarily declarative data are exempt.

Consistency and reuse

Before implementing or changing a feature, inspect nearby code and comparable features to identify established project patterns.

Prefer extending existing lexer, parser, AST, runtime, protocol, and Truffle abstractions over introducing parallel implementations.

New work must fit the conventions of the layer being modified.

Keep responsibilities clear between:

- lexical analysis
- parsing
- semantic AST
- semantic validation
- runtime object model
- invocation and dispatch
- execution contexts
- Truffle execution
- standard protocols
- host interoperability

Do not move behavior between these layers merely for convenience if doing so obscures the semantic model.

Documentation

Developer documentation must describe the current implementation and specification accurately.

Do not document planned behavior as though it already exists.

When implementation differs intentionally from the specification because work is incomplete, state that explicitly.

Examples in documentation must use valid Protos syntax according to the current grammar.

The README is an introduction to the project, not an alternative language specification. Detailed semantic rules belong in the canonical specification documents.

Development language

All development-related content MUST be written in English.

This includes:

- Java package, class, method, field, variable, and internal identifier names
- Protos implementation identifiers
- source code comments
- Javadocs and other developer documentation
- test names and test descriptions
- README files
- specification documents
- CHANGELOG entries
- commit messages
- configuration comments
- developer-facing log and diagnostic messages

The language used by the user when giving instructions does not affect the development language: all development artifacts must remain in English.

User-facing Protos programs and future localization facilities are separate concerns and must not force implementation or developer-facing content into a particular natural language.

Implementation versioning and changelog

Every committed change that modifies executable implementation source under
`src/` or distributable Protos library source under `protos/lib/` MUST:

- increment the Maven project implementation version in `pom.xml` exactly once
  in that commit; during active `0.x` development, use the next patch
  `-SNAPSHOT` version unless the user explicitly approves a different
  major/minor transition;
- add a corresponding section to the root `CHANGELOG.md` for that exact
  implementation version, describing the notable implementation changes in
  English;
- keep specification revisions separate: specification-only or
  documentation-only commits do not increment the implementation version merely
  because `spec/` or `docs/` changed, and normative specification changes remain
  recorded in `spec/PROTOS_SPEC_CHANGELOG.md`.

A commit that changes both implementation and documentation follows the
implementation rule above. A version bump must not be omitted merely because the
implementation change is incremental, internal, or part of a longer sequence of
work.

## Release publication discipline

Implementation publication and user-facing release publication are separate
operations.

A Maven implementation version ending in `-SNAPSHOT` is an exact development
trace point, not an instruction to create a Git tag, GitHub Release, release
asset, compatibility promise, or support window. Agents MUST NOT publish a
release merely because the implementation version advanced, tests passed, an
implementation slice closed, or `main` was successfully pushed.

Public releases are selected milestone revisions. They may skip any number of
internal implementation versions. Creating a tag or GitHub Release requires an
explicit release decision for the exact candidate revision; ordinary permission
to commit/push implementation work does not imply permission to release it.

CI snapshot artifacts, when configured, are development artifacts rather than
releases. Agents MUST keep that distinction visible in names, documentation,
reports, and automation.

While Core v0.1 remains a draft, public GitHub Releases should normally be marked
as pre-releases unless an explicit project decision changes the release channel.

A distributable Protos build MUST declare and validate its supported runtime/JDK
contract. Do not advertise generic Java compatibility merely because the shaded
JAR can start on a particular JDK; Truffle/Graal optimizing-runtime support and
fallback execution must not be conflated without evidence.

Release automation MUST preserve exact source-revision identity, required
validation, license/notices, runtime requirements, release metadata, and artifact
checksums. It MUST NOT automatically turn every successful `main` revision or
implementation-version bump into a public release.

The complete non-normative release policy and DIST001 slice boundaries are in
`docs/project/DIST001_RELEASE_POLICY.md`.

Task completion

At the end of a coding task, summarize:

- what changed
- which files were changed
- whether the specification was affected
- whether tests were run
- which formatter, linter, compiler, or static-analysis checks were run, and any applicable checks that were not run
- any remaining ambiguity, limitation, or follow-up that is directly relevant to the requested task
- whether license compliance was checked when source files were added or modified

Do not claim tests passed if they were not run.

Do not claim behavior is specification-compliant unless the relevant semantics were actually checked against the canonical documents.

### Documentation editing

When editing existing documentation:

- Make minimal, localized edits. Do not reflow, rewrap, or reconstruct surrounding paragraphs unless explicitly required.
- Preserve existing line breaks, paragraph boundaries, Markdown formatting, and intentional whitespace outside the exact text being changed.
- Do not concatenate words, punctuation, inline code, or Markdown elements across edit boundaries.
- After editing, inspect the actual resulting file content around every changed region rather than relying only on the generated patch.
- Before reporting completion, run `git diff --check` and review `git diff` for accidental whitespace, missing spaces, line-break changes, or unrelated formatting changes.
- Treat existing Markdown hard line breaks (two trailing spaces) as intentional unless the task explicitly requests formatting cleanup.
- Do not perform unrelated formatting cleanup in a semantic specification revision.

### License compliance

Protos is licensed under the Adaptive Public License 1.0 (APL-1.0).

The canonical repository license is `LICENSE.TXT`, including the completed Exhibit A. Do not modify the APL license text, Exhibit A selections, Initial Contributor information, Designated Web Site, governing jurisdiction, Third Party selection, patent selection, or other license configuration unless the user explicitly requests a licensing change.

For every new Protos-owned source-code file, add the exact APL Part 5 License Notice at the beginning of the file before any package declaration, imports, shebang, or source content, using the comment syntax appropriate for that file type.

For existing Protos-owned source files that are modified, verify that the required APL Part 5 License Notice is present and still matches the notice in `LICENSE.TXT`. Line wrapping and comment-prefix characters may differ, but the notice text itself must not be paraphrased, shortened, replaced with only an SPDX identifier, or otherwise altered.

The current Java form of the required notice is:

```java
/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
```

Do not automatically apply the Protos APL notice to generated files, vendored files, third-party source, Maven Wrapper files, dependencies, imported fixtures, or other material not owned by the Protos project. Preserve upstream copyright and license notices on such files.

When adding a new source-code file type or source directory, determine whether it is Protos-owned Source Code under the APL and, if so, add the corresponding Part 5 notice using a syntactically valid comment form.

Keep current project documentation and build metadata consistent with the active license:

- `README.md` must identify APL-1.0 and link to `LICENSE.TXT`.
- Maven license metadata must identify Adaptive Public License 1.0 / SPDX `APL-1.0`.
- Distributed JAR artifacts must continue to contain `META-INF/LICENSE.TXT`.
- Do not reintroduce current SSPL licensing references.
- Historical documentation may describe the license that applied at that historical time, but must not misleadingly imply that APL applied retroactively.

Before completing any coding task that creates, removes, renames, or modifies source files, perform a license-compliance check:

- verify every new Protos-owned source file has the required Part 5 notice;
- verify modified Protos-owned source files still have the required notice;
- verify no Protos APL notice was added to third-party or generated material;
- verify `LICENSE.TXT` still exists;
- if build configuration affecting packaging was changed, verify that `META-INF/LICENSE.TXT` remains included in the distributed JAR.

Do not change licensing terms or make licensing-policy decisions implicitly as part of unrelated work. Report any ambiguity before making such a change.

### Concurrency design work

The global specification revision is the newest entry in `spec/PROTOS_SPEC_CHANGELOG.md`.
Individual specification documents do not carry independent revision numbers; changelog entries identify affected documents/domains and Git provides exact per-file history.

`docs/design/CONCURRENCY_DESIGN.md` is entirely non-normative. It preserves
unresolved, directional, and historical concurrency-design material after the
legacy concurrency ledger was retired.

Status labels such as `CLOSED AS DIRECTION`, `DIRECTION CLOSED`,
`DETAILS OPEN`, `API OPEN`, `OPEN`, or `PENDING` describe design-work state only.
They never grant normative authority to the design document.

When a design decision becomes language semantics:

- update the real owning normative specification under `spec/`;
- update every other normative owner genuinely affected by the decision;
- record the change in `spec/PROTOS_SPEC_CHANGELOG.md`; and
- update or annotate the design note so it no longer appears unresolved.

Never implement observable behavior directly from `docs/design/CONCURRENCY_DESIGN.md`.

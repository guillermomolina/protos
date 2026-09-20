# Protos design work

These instructions apply to `Dxxx`, `PLATxxx`, `UPSTREAMxxx`, and to any
substantive semantic or architectural decision, regardless of the family that
discovered it.

Substantive semantic/architectural decisions route uniformly through
`Dxxx`/`PLATxxx` and the GITHUB010 process below. The lighter research
guidance in `spec/AGENTS.md` applies only to already-tracked, non-substantive
specification refinements; it must not replace this process for substantive
questions.

Investigation contract: exhaustive. The purpose of this work class is to
resolve the design space, so whole-document normative reads and the
cross-system research below are required. Apply the exhaustive-read
discipline in "Truncated repository reads" below when documents are large.

The Protos design philosophy in `AGENTS.work/REFERENCE.md` constrains every
candidate compared here; load it before constructing candidates.

Durable records follow the DOC002 role-first path policy in
`AGENTS.work/REFERENCE.md`.

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

### Preserve owner-approved decision invariants through ratification
<!-- GITHUB021 OWNER-DECISION-INVARIANT-PRESERVATION -->

During an active substantive decision, an explicit project-owner statement that
establishes a semantic or architectural invariant, rejects a previously assumed
model, or corrects the agent's model of the decision is part of the decision's
approval history. Record such a materially constraining invariant in the active
decision/work record as soon as practical so later candidate construction and
ratification can review it directly.

This rule is deliberately bounded. Do not transcribe every conversational remark
or exploratory preference into permanent policy. Record explicit owner decisions
and model corrections that materially constrain the active decision, including
the boundary they establish and enough context to distinguish the approved
invariant from surrounding unresolved questions.

A later candidate, recommendation, implementation strategy, lowering, pseudocode
model, analogy, or ratification summary MUST NOT silently contradict an already
owner-approved invariant merely because the contradiction is embedded inside a
larger candidate. If later evidence genuinely warrants changing, narrowing, or
removing that invariant, identify the exact conflict, the previously approved
invariant, and the proposed replacement consequence, then explicitly reopen that
point for project-owner decision before dependent work proceeds.

Implementation models are not semantic authority. A lowering such as
"construct X by repeatedly invoking operation Y", host/runtime pseudocode, or an
implementation analogy may be useful evidence for evaluation order, effects, or
feasibility, but it does not become the language/design contract unless the
semantic equivalence itself was explicitly selected. Preserve the existing
specification-authority and semantics-before-implementation rules when moving
between semantic reasoning and implementation machinery.

Before requesting final project-owner approval of an exact `Dxxx`/`PLATxxx`
candidate, and again before publishing its durable ratification record when one
is required, perform an invariant/delta consistency check against the explicit
owner-approved invariants recorded for that decision. The review MUST surface:

- each applicable recorded invariant and whether the candidate preserves it;
- any observable consequence that changes, narrows, or contradicts one;
- any materially new semantic/architectural consequence introduced by candidate
  refinement since the earlier owner decision; and
- the explicit approval provenance for every reopened invariant whose value
  changed.

The consistency check passes only when the candidate preserves the applicable
invariants or every conflicting invariant has been explicitly reopened and the
replacement consequence explicitly approved. Approval of a candidate name,
label, bundle, or summary does not retroactively approve a hidden contradictory
consequence that was not surfaced. Exact-candidate approval remains exact in
scope.

D136/#542 is the motivating failure mode, not a semantic resolution inside this
policy. If the active record says that `%{...}` defines construction-time initial
Map associations and a later model claims that `%{...}` is semantically repeated
post-construction `atPut`, the conflict must be surfaced and reopened; the later
model cannot silently supersede the recorded invariant.

## Exhaustive comparative research for Dxxx and PLATxxx
<!-- GITHUB010 EXHAUSTIVE-DECISION-RESEARCH -->

A newly allocated `Dxxx` or `PLATxxx` is an unresolved research problem, not an
invitation to select the first locally plausible solution.

Before asking the project owner to approve a substantive `Dxxx` or `PLATxxx`,
the agent MUST perform and present a comparative investigation broad enough that
the recommendation is not merely the result of local familiarity,
implementation convenience, or a small cherry-picked precedent set.

The goal is not to imitate another language, runtime, VM, operating system, or
tool. Prior art is evidence used to understand the design space, failure modes,
scaling limits, migration costs, and long-term consequences before selecting the
solution that best fits Protos.

### Dxxx research breadth

For an implementation-independent `Dxxx`, investigate the same or closely
analogous problem across materially different language/runtime design families.

The comparison MUST normally include at least five credible systems spanning at
least three meaningfully different design approaches, unless the domain has
fewer relevant precedents. If a well-known relevant system is omitted, state why
it is not materially comparable.

Depending on the question, useful prior art may include prototype/object
languages, dynamic languages, static languages, functional/concurrent
languages, capability-oriented systems, distributed languages, language
specifications, or published research.

Examples include Self, Smalltalk/Pharo, Io, JavaScript, Lua, Ruby, Python, Java,
C#, C++, Rust, Go, Swift, Erlang/Elixir, Pony, and other systems exposing a
materially different solution.

Do not compare only syntax, API spelling, or marketing-level behavior. Compare
the underlying semantics: authority/ownership, identity, lifetime, failure,
ordering, cancellation, concurrency/distribution consequences, implementation
freedom, and known limits where relevant.

### PLATxxx research breadth

For a platform/runtime `PLATxxx`, investigate how relevant production runtimes,
language implementations, VMs, compilers, operating systems, or native
substrates solve the same architectural problem rather than reasoning only from
the current Protos implementation.

For a Truffle-related decision, the investigation MUST survey the relevant
public Truffle implementation space rather than citing only one or two familiar
languages. Consider, where applicable, GraalJS, TruffleRuby, GraalPy, Espresso,
Sulong/LLVM, SimpleLanguage and other active or historically informative
Truffle implementations. Apple Pkl MUST be included in the survey when its
implementation surface is materially comparable; otherwise record why it is not
relevant to the specific decision.

The survey MUST also look outside Truffle when mature non-Truffle systems provide
useful evidence. Depending on the problem, this may include HotSpot/OpenJDK,
LLVM, V8, .NET/CLR, Swift/Apple compiler/runtime infrastructure, BEAM,
operating-system kernels, schedulers, databases, distributed runtimes, storage
systems, or other systems facing the same architectural constraint.

For OS/native/platform boundaries, do not assume Linux/JVM behavior is
universal. Compare Linux, BSD, macOS/Darwin, Windows, or other hosts whenever
their differences can affect the choice.

A PLATxxx packet MUST explicitly consider, when relevant:

- Truffle partial evaluation and compilation/deoptimization boundaries;
- Context isolation and multi-Context behavior;
- thread, Task, Actor, Process and host-resource ownership;
- memory scaling and per-instance/per-context state;
- cancellation, unwind and failure paths;
- native-image/AOT constraints;
- Bytecode DSL migration/evolution;
- alternative schedulers/backends/runtimes; and
- portability beyond the current host platform.

### Candidate construction

Do not begin with one preferred solution and search only for confirming evidence.

The decision packet MUST expose the meaningful candidate set discovered during
research, including where applicable:

- the current/status-quo design;
- the conservative/minimal change;
- the strongest credible alternative architectures;
- real hybrid options that remove a genuine trade-off rather than merely
  accumulating mechanisms; and
- `defer / do nothing` when deferral is a real option.

A materially credible candidate may be eliminated only with an explicit reason.
Record important rejected candidates so later agents do not need to rediscover
the same design space.

### Comparative scoring

After qualitative analysis, score every surviving candidate from **1 to 5** on
all of the following dimensions:

1. **Correctness / invariant preservation** — preserves required semantics and
   architectural invariants.
2. **Protos alignment** — fits the documented Protos design philosophy,
   including small-universe design, mechanisms over institutions, ordinary
   things remaining ordinary, no unnecessary privileged entities,
   orthogonality/composability, locality, and minimal coordination.
3. **Present-need proportionality / pay for what you need** — keeps conceptual,
   implementation, user-facing, maintenance, coordination, and runtime cost
   proportional to requirements that actually exist today rather than charging
   current users or the project for speculative capability.
4. **Incremental growth / grow as you need** — permits the smallest sufficient
   design today to acquire plausible later capability without invalidating its
   fundamental model or requiring speculative machinery to be installed in
   advance.
5. **Future-option resilience** — preserves plausible future
   language/runtime/library/backend choices instead of prematurely closing them,
   while distinguishing preservation of an option from preimplementation of it.
6. **Scalability** — remains sound as objects, data, Tasks, Actors, Processes,
   Contexts, threads, nodes, workloads, and concurrency increase.
7. **Conceptual simplicity** — minimizes total semantic/architectural
   complexity, special cases, hidden rules, and interaction surface rather than
   merely implementation line count.
8. **Portability / implementation freedom** — avoids unnecessary coupling to
   one VM, host mechanism, OS, compiler strategy, runtime identity, or current
   implementation accident.
9. **Runtime / resource cost** — CPU, allocation, memory, synchronization,
   coordination, startup, and other machine costs imposed by the design,
   including costs paid when optional capability is unused.
10. **Failure / operability** — predictable failure modes, cancellation/unwind
    behavior, diagnosability, observability, and recovery of broken invariants.
11. **Cost of deferral / reversibility / migration** — identifies both the cost
    of changing an adopted choice later and the concrete rewrite, compatibility,
    persisted-state, deployment, semantic, or architectural cost of omitting a
    capability now and adding it later.
12. **Evidence maturity / implementation risk** — quality of precedent and
    feasibility evidence versus unproven assumptions or implementation risk.

Each score MUST include a short justification. When evidence is uncertain, mark
the score confidence as `HIGH`, `MEDIUM`, or `LOW`.

Domain-specific criteria MAY be added, but the common criteria above MUST NOT be
silently removed.

### Incremental-design balance and anti-overengineering gate

Future resilience and scalability are requirements to evaluate, not permission
to pre-build speculative capability. Every substantive decision comparison MUST
balance them explicitly against present necessity, incremental growth, and the
concrete cost of deferring capability.

In addition to the common scoring dimensions above, every surviving candidate
MUST explicitly answer these questions:

**Pay for what you need**

Is the conceptual, implementation, user-facing, runtime, resource, maintenance,
and coordination cost paid today proportional to requirements that actually
exist today? Identify who pays for capability that the motivating use case does
not currently need.

**Grow as you need**

Can the design begin with the smallest sufficient capability and acquire more
capability later without invalidating its fundamental model? Distinguish
incremental extension from a future redesign disguised as extensibility.

**Cost of deferral / reversibility**

If a capability is omitted today, what exactly would have to change to add it
later? Identify whether deferral would require changing public semantics,
identity, authority, ownership, scheduling, persistence, data representation,
compatibility contracts, or other foundational structure, rather than merely
adding an implementation or library layer.

High concrete deferral cost may justify sophisticated architecture before all of
its capability is immediately exercised. The burden is to demonstrate that cost;
a generic claim that a future implementation would otherwise require a rewrite
is not sufficient.

**Smallest sufficient solution**

What is the smallest design that satisfies the requirements and invariants known
today? For every capability beyond that design, identify the concrete current
requirement, invariant, evidence, or demonstrated deferral cost that justifies
including it now.

**Speculation burden of proof**

A plausible future requirement is evidence for preserving an escape path, not by
itself evidence for implementing the requirement now. If future capability can
be added later at bounded cost without breaking the model, prefer preserving the
option over paying its complexity in advance.

The comparison MUST therefore distinguish:

    future-compatible
        from
    future-preimplemented

A design may deliberately leave capability absent while still being the more
future-resilient choice.

### Non-compensating design red flags

Arithmetic totals MUST NOT allow strong scalability or future-option scores to
silently compensate for unjustified present complexity.

A candidate with poor pay-for-what-you-need behavior, unnecessary public or
conceptual surface, or no credible incremental-growth path carries an explicit
overengineering red flag. The recommendation MUST address that red flag
directly.

Likewise, choosing the smallest current implementation is not automatically
correct. A minimal candidate carries an explicit underengineering red flag when
credible evidence shows that deferral would force a foundational rewrite,
compatibility break, semantic migration, or replacement of an architecture whose
boundary must be established now.

These red flags are qualitative review gates, not additional numbers to hide in
an arithmetic total.

Scores are comparison aids, not mathematical authority. Do not select a
candidate merely because its arithmetic total is largest. A hard semantic
constraint, qualitative threshold, catastrophic failure mode, unacceptable
future lock-in, fundamental Protos-philosophy violation, unjustified speculative
complexity, or demonstrated unacceptable deferral cost may disqualify an
otherwise high-scoring candidate.

### Mandatory adversarial incremental-design questions

Before recommending a candidate, the decision packet MUST explicitly answer:

**What is the smallest solution that satisfies the requirements we have today,
and what concrete evidence justifies every capability beyond it?**

**If we omit this capability today, can it be added later without breaking the
model?**

**If we do not build this capability now, what exactly must be rewritten later
to add it?**

**What current complexity would make us regret implementing the future
requirement before we actually need it?**

Answers MUST be specific to the decision. Generic claims such as "more
future-proof", "more scalable", "simpler", or "we would have to rewrite it
later" do not satisfy this gate without identifying the affected contracts,
boundaries, state, semantics, implementation layers, or migration work.

### Future-scenario stress test

Before recommending a candidate, actively test it against plausible future
scenarios beyond the immediate motivating case.

Where relevant, include:

- much larger workloads and long-lived systems;
- many Tasks, Actors, Processes or Contexts;
- multicore/high-concurrency execution;
- distributed/multi-node execution;
- cancellation, failure and unwind;
- alternative schedulers;
- persistence/serialization;
- alternative Standard Library implementations;
- alternative host runtimes/operating systems;
- migration away from current Truffle machinery;
- Truffle Bytecode DSL/compiler evolution; and
- plausible future Protos features that must compose with the decision.

Every substantive Dxxx/PLATxxx packet MUST explicitly answer:

**What plausible future requirement would make us regret selecting this option?**

and:

**If that happens, what escape path remains?**

### Required decision packet

Before requesting project-owner approval, the packet MUST contain:

1. the exact decision and why it is needed;
2. current Protos constraints and already-ratified decisions;
3. the prior-art/systems survey and what each comparison contributes;
4. the complete meaningful candidate set;
5. the comparative 1–5 scoring matrix with confidence where needed;
6. failure modes, counterexamples, and disqualifying conditions;
7. future-scenario and scalability stress analysis;
8. incremental-design analysis covering smallest sufficient solution,
   pay-for-what-you-need, grow-as-you-need, and concrete cost of deferral;
9. implementation/runtime/resource consequences, including present costs paid
   for capability not currently required;
10. portability, migration, compatibility, and reversibility consequences;
11. intentionally deferred questions, including why deferral is safe or unsafe;
12. the agent's recommended option and why it is the most Protos-aligned choice;
    and
13. the strongest argument **against** the recommendation.

The agent MUST stop at that point for explicit project-owner approval unless the
specific bounded decision has already been explicitly delegated.

A short investigation that merely finds one plausible implementation, cites one
or two familiar precedents, or states that an approach is common practice does
not satisfy this rule.

## Project decision families

Formal decision identifiers are orthogonal to the documentation role selected by
DOC002-F0 and to normative specification authority:

- `Dxxx` identifies implementation-independent decisions. A Dxxx record may have
  a language/specification role under
  `guillermomolina/protos-project-docs:docs/project/decisions/language/` or a
  tooling/package-system role under
  `guillermomolina/protos-project-docs:docs/project/decisions/tooling/`; the prefix
  alone does not select the role. Observable Protos semantics are normative only
  through the applicable ratified material under `spec/`.
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

### External upstream evolution family
<!-- GITHUB014 UPSTREAM-FAMILY-CONTRACT -->

`UPSTREAMxxx` tracks evaluation and coordination for releases, features, defects,
deprecations, compatibility changes, and direct collaboration in external
projects that may affect or benefit Protos. It exists so external evolution can
be investigated durably without being misclassified as implementation work in
whatever Protos family happened to discover it.

An `UPSTREAMxxx` item MAY retain exact external project/release/PR/Issue
identities, version/SHA/toolchain baselines, compatibility experiments, upstream
maintainer feedback, reproducible evidence, and later re-evaluation triggers.
It MUST NOT by itself authorize a Protos dependency upgrade, adoption, migration,
semantic change, durable platform choice, or roadmap commitment.

Each upstream evaluation SHOULD classify its Protos impact as one of:

- `NO_ACTION` — no Protos change is justified;
- `BENEFICIAL_BUT_NOT_ACTIONABLE` — useful evidence exists but prerequisites or
  current production applicability are absent; or
- `ACTION_REQUIRED` — concrete Protos work is justified.

`ACTION_REQUIRED` is a routing result, not implementation authority. Allocate or
link the proper `Ixxx`, `PERFxxx`, `DISTxxx`, `BUGxxx`, `TOOLxxx`, `CLIxxx`,
`LIBxxx`, or other owning work item. If the external change exposes a substantive
Protos semantic or durable architecture choice, route that choice through the
normal `Dxxx`/`PLATxxx` approval gate before dependent implementation proceeds.
The `UPSTREAMxxx` item may close once impact is classified and any required
Protos work has a proper owner; derived work need not finish first.

`UPSTREAMxxx` uses the existing canonical lifecycle status vocabulary. Active
evaluation may legitimately be `status:ready` or `status:in-progress` and appear
in the GITHUB025 Work queue. Waiting for an upstream response, release, or Protos
prerequisite may use `status:paused`. Status, not the `UPSTREAM` family itself,
determines ordinary Project actionability. Do not invent a separate upstream
lifecycle state or blanket-exclude the family from Project views.

Durable records follow the DOC002 role-first path policy in
`guillermomolina/protos-project-docs`: work records primarily owned by the item
belong under `docs/project/work/UPSTREAMxxx/` in that repository; immutable or
snapshot-like experimental evidence belongs under
`docs/project/evidence/UPSTREAMxxx/`. Do not create a parallel
`docs/project/upstream/` hierarchy merely for classification.

The canonical registry for platform/runtime architecture decisions is
`guillermomolina/protos-project-docs:docs/project/registries/PLATFORM_ARCHITECTURE_DECISIONS.md`.

## Truncated repository reads

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

## Specification changes

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

## Concurrency design work

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

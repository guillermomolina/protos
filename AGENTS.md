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

### Retrospective complexity and necessity audits

When auditing an already implemented feature or architecture, historical
development effort is sunk cost and MUST NOT by itself justify keeping or
removing the design. Evaluate the complexity the project is still paying.

The audit MUST distinguish, where applicable:

- programmer/user cognitive cost;
- semantic, syntax, protocol, or API surface cost;
- ongoing implementation and maintenance cost;
- drag imposed on unrelated or future feature development;
- duplicated semantic or execution machinery;
- runtime, memory, startup, synchronization, or resource cost;
- compatibility and migration cost;
- capability currently obtained from the mechanism;
- robustness and failure containment;
- scalability and future-option resilience; and
- the cost and feasibility of removing the mechanism now and reintroducing a
  capability later if real evidence appears.

A retrospective audit SHOULD classify each reviewed mechanism into one of three
outcomes:

1. **KEEP** — its current or foundational value justifies the complexity that
   remains.
2. **REMOVE_NOW / RECONSIDER_LATER** — its current cost or surface is not
   justified, but future evidence could make substantially the same capability
   worth designing again.
3. **REMOVE_PERMANENTLY** — the concept conflicts with Protos philosophy,
   duplicates a better mechanism, creates an undesirable semantic category, or
   is fundamentally inferior even if related future requirements appear.

`REMOVE_NOW / RECONSIDER_LATER` does not reserve syntax, semantics, implementation
hooks, compatibility behavior, parser productions, runtime branches, dormant
abstractions, or other scaffolding "just in case". Remove the mechanism fully
within the approved scope. If future evidence justifies the capability, redesign
it from the then-current Protos model and evidence.

To distinguish the two removal outcomes, explicitly ask:

**If future evidence justified this capability, would we want to reintroduce
substantially the same concept?**

If yes, prefer `REMOVE_NOW / RECONSIDER_LATER`. If no, prefer
`REMOVE_PERMANENTLY`.

A useful retrospective evidence matrix is:

    FEATURE
    CURRENT_REAL_USE
    USER_VISIBLE_COMPLEXITY
    ONGOING_MAINTENANCE_COST
    RUNTIME_COST
    PAY_FOR_WHAT_YOU_NEED
    GROW_AS_YOU_NEED
    COST_OF_DEFERRAL / REVERSIBILITY
    FUTURE_RESILIENCE
    SCALABILITY
    PROTOS_PHILOSOPHY
    OUTCOME

The classification itself does not authorize removal. If removing or retaining a
mechanism crosses the substantive design approval gate, present the evidence and
recommended classification to the project owner before changing semantics,
architecture, or compatibility.

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
in the GITHUB012 Work queue. Waiting for an upstream response, release, or Protos
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

<!-- DOC002 ROLE-FIRST-DOCUMENTATION-PATH-POLICY -->
## Durable project documentation path policy

DOC002-B ratified the role-first information architecture for durable project
documentation. Durable project records now live in
`guillermomolina/protos-project-docs`; `guillermomolina/protos` remains the
operational control plane for formal identifiers, GitHub Issues, owner
coordination, approvals, and derived Project scheduling.

The canonical non-normative path contract is
`guillermomolina/protos-project-docs:docs/project/work/DOC002/DOC002_DOCUMENTATION_PATH_CONTRACT.md`.

Agents creating a **new** durable record under `docs/project/**` MUST create it
in `guillermomolina/protos-project-docs` and place it in the selected role-first
destination when the role and owner are unambiguous:

- `docs/project/work/<formal-work-item>/` for records primarily owned by one
  formally tracked work item;
- `docs/project/decisions/language/` for durable decision records whose
  primary domain is observable language/specification semantics; `Dxxx` is not
  by itself a language-role classifier;
- `docs/project/decisions/tooling/` for durable implementation-independent
  tooling/package-system/Package Tool/Test Tool decisions that neither change
  observable Protos semantics nor select host/runtime architecture;
- `docs/project/decisions/platform/` for durable `PLATxxx` platform/runtime
  decision records;
- `docs/project/architecture/` for cross-cutting implementation architecture;
- `docs/project/governance/` for repository/project rationale and maintained
  policy that does not need an invented formal work family;
- `docs/project/registries/` for durable registries and closure/evidence ledgers;
- `docs/project/evidence/<formal-work-item>/` for immutable or snapshot-like
  evidence with a genuine formal owner; and
- `docs/project/history/` for retired or superseded historical snapshots.

`guillermomolina/protos-project-docs:docs/project/README.md` is the role-first
navigation entry point. Repository-root policy/legal/community files and the
established `docs/guide/`, `docs/design/`, and `spec/` authority boundaries in
`guillermomolina/protos` are not relocated merely for symmetry.

The DOC002 migration is complete. A new durable record MUST use its canonical
role-first destination in `guillermomolina/protos-project-docs` when role and
owner are unambiguous; creating a new unclassified flat durable record directly
under `docs/project/` is non-compliant.

If an unexpected legacy or unclassified durable path is discovered later, edit
it only as necessary at its actual path until an explicit bounded
classification/migration change owns relocation. Do not opportunistically move
it, guess a destination, duplicate it under the role-first tree, or create a
compatibility copy merely for symmetry. Historical records may retain old path
spellings when changing them would falsify the repository state they document.

References to current registries, decisions, architecture and work records MUST
identify `guillermomolina/protos-project-docs` as their repository. In
particular, the blocker/unblock-condition ledger is
`guillermomolina/protos-project-docs:docs/project/registries/IMPLEMENTATION_BLOCKERS.md`,
the implementation/closure ledger is
`guillermomolina/protos-project-docs:docs/project/registries/IMPLEMENTATION_STATUS.md`,
and the platform decision registry is
`guillermomolina/protos-project-docs:docs/project/registries/PLATFORM_ARCHITECTURE_DECISIONS.md`.

If a new durable document does not fit one selected role cleanly, do not invent
a new directory, identifier family, or classification to force symmetry.
Resolve the classification through the owning tracked work or an explicit
documentation-governance decision before establishing a durable path. For
decision records specifically, classify by the decision's primary domain
(language/specification, tooling/package-system, or platform/runtime), never
solely by identifier prefix.

DOC002-A remains a historical audit snapshot, not a permanent placement manifest.
The durable rule is structural: `guillermomolina/protos-project-docs` must keep
`docs/project/**` role-first, and later work that discovers a real
classification ambiguity must resolve it explicitly rather than silently
weakening the layout.

## Current project coordinates

The following identifiers are the repository's current operational coordinates.
They are exact spellings, not names that agents should reconstruct from a
person's name, memory, convention, or an approximate prompt:

- GitHub repository: `guillermomolina/protos`
- Durable project-record repository: `guillermomolina/protos-project-docs`
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


## Protos capability and library reuse
<!-- GITHUB023 IDIOMATIC-PROTOS-CAPABILITY-LIBRARY-REUSE -->

For hand-written Protos source, agents MUST inspect the existing Protos language,
protocols, Standard Library, and bundled libraries before implementing equivalent
functionality locally. When an established Protos facility directly satisfies the
required semantics, prefer that facility over a private parallel implementation.

Use the highest-level established Protos capability that accurately and clearly
expresses the required behavior. This includes ordinary language/protocol
operations, existing collection selection/transformation/lookup/traversal
facilities, Standard Library and bundled-library APIs, and the established Protos
documentation mechanism when public Protos APIs require developer-facing
documentation.

In particular, a Tool or application MUST NOT embed a private parser, serializer,
collection utility, version parser, testing helper, filesystem/process facility,
or equivalent subsystem merely for implementation convenience when an existing
Protos facility satisfies the same contract. For example, when a Tool needs TOML
and the ratified Protos TOML library satisfies its requirements, use that library
instead of introducing a Tool-local TOML parser. Apply the same rule to JSON,
text/encoding, semantic versions, testing, collections, filesystem/process
facilities, and other already-supported domains.

Reuse is not mechanical feature usage. Do not use an abstraction merely because
it exists when a simpler or lower-level form is materially clearer or when the
existing facility does not provide the required semantics. A local implementation
is appropriate when reuse would create a bootstrap or layering cycle, violate an
isolation/authority/ownership boundary, when the existing capability is itself
being implemented or directly tested, or when measured requirements justify a
distinct implementation. Make a non-obvious exception explicit in the code,
work item, or implementation report.

If the required reusable capability is missing or semantically insufficient, do
not silently work around the gap by growing a private substitute inside the
consumer. Route the missing capability to the proper language, library, Tool, or
other owning work item unless the consumer-specific implementation is itself the
correct established owner.

This policy complements the syntactic source-style rule above: that rule governs
how equivalent Protos syntax is normally spelled; this rule governs which
existing Protos capabilities ordinary repository code should reuse.


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

## GitHub connector/API repository-write safety
<!-- GITHUB-CONNECTOR-WRITE-SAFETY -->

GitHub connector/API access has two distinct roles: **read/live coordination**
and **repository publication**. Agents MUST NOT blur those roles merely because
the active connection has `push` or `admin` permission.

Capability, permission, connectivity, existence, schema, and error-path probes
MUST be read-only. An agent MUST NOT call repository-content or Git-ref mutation
operations such as `create_file`, `update_file`, `delete_file`, `create_commit`,
`update_ref`, or equivalent REST/GraphQL/connector writes merely to discover
whether a write would succeed. In particular, NEVER create sentinel/probe
artifacts such as `noop`, `test`, `NONEXISTENT`, `nonexistent`, empty files, or
temporary commits in the repository.

Repository-content changes follow **Interactive local contribution and
publication workflow** below. Connector/API repository-content writes MUST NOT
bypass that workflow. The sole agent-direct exception is
`guillermomolina/protos-project-docs`, governed by the bounded exception below.

### Sole autonomous repository-publication exception
<!-- GITHUB022 PROTOS-PROJECT-DOCS-PUBLICATION-EXCEPTION -->

The project owner grants standing repository-publication authorization for
exactly one repository: `guillermomolina/protos-project-docs`. This is the only
repository an agent may autonomously edit, commit, and push repository-content
changes to without another per-publication confirmation when the active task
requires a bounded durable project-documentation change.

This authorization is intentionally direct. For
`guillermomolina/protos-project-docs`, an authorized agent SHOULD perform the
repository-content edit and its normal non-force publication itself through the
available repository mechanism. Do not hand the maintainer shell commands or a
Markdown ZIP merely so the maintainer can apply, commit, or push a documentation
change in that repository.

When network-capable Git transport is available, the agent may use the normal
non-force Git publication path from its working checkout; an isolated worktree is
not required solely to satisfy a handoff rule. When normal Git transport is
unavailable in the current execution environment but an authenticated GitHub
connector/API has repository write capability, that repository alone MAY use the
following bounded Git Data API publication fallback. This fallback is a governed
publication path, not a general exemption from repository-write safety.

The fallback MUST:

1. read the current `main` ref and record its exact commit as
   `PUBLICATION_BASE`;
2. read the exact target files plus the base commit/tree required to construct
   the bounded change;
3. materialize the complete intended final bytes outside repository history and
   perform the validation required by the definitive documentation/governance
   delta before moving any ref;
4. create only the blobs/tree/commit required for that validated delta, with the
   new commit's sole parent exactly `PUBLICATION_BASE`;
5. re-read `main` immediately before publication and abort if it no longer
   equals `PUBLICATION_BASE`;
6. update `refs/heads/main` only by a non-force update to that exact validated
   commit; never force-update, merge, rebase, or guess through concurrent
   movement;
7. re-read `main`, the exact published commit, the changed-path set, and the
   published target files after the ref update, and report the exact published
   SHA; and
8. preserve every applicable `AGENTS.md`, scope, validation, authority,
   approval, and durable-record requirement exactly as the normal Git path
   would.

The API fallback MUST NOT use `create_file`, `update_file`, `create_blob`,
`create_tree`, `create_commit`, `update_ref`, or equivalent mutations as
capability or connectivity probes. It may invoke those operations only as the
already-authorized publication transaction after all required read-only checks
and pre-publication validation have succeeded. It MUST NOT create a temporary
remote branch.

This standing authorization applies only to the exact repository
`guillermomolina/protos-project-docs`. All other Protos repository-content
changes use maintainer-executed handoff. Separately governed live GitHub
coordination mutations may be performed when the task requires them, but MUST
NOT be used as generic write-capability probes.

Read-only GitHub operations may inspect repository metadata, files, commits,
refs, checks, workflow results, Issues, Pull Requests, and other state whenever
needed. If the available interface exposes only a mutating operation for a
question that can be answered safely another way, use the read-only path. If
write capability remains uncertain, **fail closed and report the limitation**;
do not discover it by mutating the repository.

If an accidental connector/API repository-content or Git-ref mutation is
detected, stop the affected work immediately and report the exact commit/ref and
paths changed. Do not silently hide the incident with a second cleanup commit or
continue publication as though the mutation had not happened. Recovery must be
an explicit bounded action that preserves enough evidence to understand what was
changed and why.

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
inherited identity. Repository-wide reconciliation is an explicit audit/repair
operation, not the routine propagation path.

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

When such an independently tracked child Issue is created, its native GitHub
parent relationship MUST also be established under the **Native GitHub Issue
hierarchy** rule above; a textual parent note alone is not sufficient.

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

When the workflow requires the maintainer to execute commands manually, provide
one safe bounded slice at a time. After the maintainer reports successful
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
does not waive the repository/specification audit when repository contents are
available through another read-capable mechanism.

Repository-content execution follows **Interactive local contribution and
publication workflow** below. Lack of local tooling MUST NOT be used to invent a
different publication path.


### Interactive local contribution and publication workflow
<!-- GITHUB002-C HYBRID-CONTRIBUTION-PUBLICATION-CONTRACT -->
<!-- GITHUB024 INTERACTIVE-LOCAL-PATCH-HANDOFF -->

Live GitHub coordination is governed by the dedicated GitHub sections below;
this section owns repository-content execution and publication.

The current publication model is:

- **External contributions** normally use a branch/fork + Pull Request + CI/review
  before merge.
- **Maintainer/agent interactive work outside
  `guillermomolina/protos-project-docs`** uses the maintainer's current checkout
  with bounded command handoff and adaptive local validation; the maintainer
  executes repository-changing commands, including commit and push.
- **`guillermomolina/protos-project-docs` work** uses the standing direct
  publication exception above.
- **Maintainer Pull Requests are optional** and SHOULD be used when their review,
  integration, audit, or collaboration value justifies their operational cost.

For ordinary maintainer-directed work in repositories subject to handoff,
agents MUST work from the repository state the maintainer is actually using and
provide the required commands in small, coherent executable blocks. Do not create
temporary branches, temporary worktrees, staging repositories, or out-of-checkout
publication environments merely to apply an ordinary interactive change. This
handoff rule does not apply to the agent-direct `protos-project-docs` exception.

Before editing, inspect the current status and every patch-owned path relevant to
the next command block. A globally clean checkout is not required. Existing
tracked or untracked work outside the patch-owned paths MUST remain untouched.

When a patch-owned path already has unrelated or concurrent edits, inspect its
current content and proceed only when the requested transformation can preserve
those edits without silently absorbing or overwriting them. Otherwise stop and
report the overlap.

Agents MUST NOT manufacture a clean checkout with automatic `stash`, `reset`,
`restore`, `checkout`, `clean`, or equivalent destructive operations. They MUST
NOT use `git add -A` or `git add .`; stage only explicit patch-owned paths. Never
force-push.

Ordinary source, test, build, configuration, and similar edits in handoff
repositories SHOULD be handed off directly as bounded shell-command blocks in
chat. Prefer a few transparent commands over a large opaque generated script when
the same change can be made safely and readably.

Shell-command blocks handed to the maintainer for direct execution MUST be safe
to paste into the maintainer's existing interactive shell. Such blocks MUST NOT
contain `set -e`, `set -u`, `set -o pipefail`, combined forms such as
`set -euo pipefail`, any other command that enables `pipefail`, or `exit`. Those
constructs can persist in or terminate the maintainer's shell. Use bounded
command-local checks, `&&`, or explicit conditionals when failure gating is
needed. This restriction applies to command blocks handed to the maintainer;
standalone helper scripts executed as child processes may use their own internal
failure handling when appropriate.

In handoff repositories, commit and push remain separate from editing/validation
unless the maintainer requests the corresponding commands in the same step, and
the maintainer executes those commit/push commands. Before publication,
synchronize with current `origin/main`, inspect intervening movement relevant to
the patch, and preserve unrelated concurrent work. A publication race is never
permission to force-push or silently resolve a semantic conflict.

<!-- GITHUB003 CONCURRENT-PUBLICATION-POLICY -->
When `origin/main` moves during interactive work, re-evaluate only the
preconditions and dependency surfaces that movement can affect. Benign unrelated
movement does not invalidate already-correct substantive work. If the movement
overlaps the patch, changes an applicable semantic/governance precondition, or
changes moving metadata required by the final commit, reconcile that specific
surface before publication.


### Focal validation for mechanical reconciliation children

Already-ratified, semantics-preserving mechanical reconciliation work SHOULD pay
only for the validation needed to prove the bounded child delta. In particular,
AUD003 source-style reconciliation children may use a declared focal validation
profile instead of a broad Tool-family or repository-wide integrated suite when
all of the following are true:

- the child changes only already-classified source spellings whose semantic
  equivalence is already authoritative;
- the change proves the complete scoped inventory before modification and the
  required clean/postcondition inventory afterwards;
- `scripts/source_style_guard.py` passes for the candidate;
- any executable corpus directly affected by the changed source is exercised by
  the smallest deterministic retained runner/test entry that covers that corpus;
- the child does not change specification, public API, runtime implementation,
  implementation version, or a semantic/platform decision; and
- the owning top-level executable/audit closure retains responsibility for the
  broader integrated validation required before final closure.

This is not a generic skip-tests mechanism. Each focal profile MUST name its
retained executable evidence and its validation-dependency closure. If a slice
cannot establish those facts, it falls back to the ordinary adaptive selector.

If a maintainer deliberately chooses a Pull Request for a change, the normal PR
rules still apply:

- use `Closes #N` / `Fixes #N` / `Resolves #N` only for a leaf Issue fully
  completed by merge;
- use `Refs #N` for parents/related work that must remain open;
- report the local validation performed;
- let the repository's PR CI run; and
- do not call an open PR `PUBLISHED`; publication occurs when the accepted change
  reaches `main`.

`main` is intentionally not PR-only protected at this stage. Reconsider required
PRs/checks/reviews when sustained external contribution volume or maintainer
concurrency makes the additional coordination cost worthwhile.


### Local checkout safety and Markdown handoff

The maintainer's current checkout may contain unrelated work from the maintainer
or other agents. Interactive editing MUST preserve that work.

Before each bounded edit, inspect the current changed-path set and the current
content of the paths the edit owns. Do not reject unrelated dirty paths merely
because they exist. Do not overwrite, stage, revert, clean, or otherwise
incorporate unrelated changes.

Markdown creation and editing use a ZIP artifact rather than long inline heredocs
or pasted whole-document replacements. Assume the maintainer downloads the ZIP
into the repository's `tmp/` directory.

For a **new Markdown file**, the ZIP MUST contain the complete intended Markdown
file and an `apply.sh` that installs it at the intended path. The helper MUST fail
rather than unexpectedly overwrite an existing file unless replacement is the
explicit task.

For an **existing Markdown file**, the ZIP SHOULD contain a bounded
state-aware transformation helper, normally Python when that is the clearest
reliable mechanism, plus `apply.sh`. Another helper mechanism is acceptable when
it is simpler and at least as safe. The helper MUST verify the current expected
state/anchors, perform only the intended edit, preserve unrelated content and
formatting, and fail without modification when the required state is missing or
ambiguous.

The standard Markdown bundle handoff is:

    unzip -d /tmp tmp/<bundle>.zip
    /tmp/<bundle>/apply.sh .

The agent MUST provide those commands explicitly with the artifact. Do not add
instructions requiring the maintainer to manually copy generated files from an
agent/container path.

Generated Markdown helpers are executable project work. Before delivering a ZIP,
the agent MUST validate the helper syntax when the authoring environment permits,
verify the expected archive layout and integrity, and test the transformation
against the audited current target content. If a required authoring-time check is
not available, report that limitation instead of inventing a PASS result.

Generated helpers MUST use repository-declared or baseline-compatible tools.
When a helper runtime is required, use syntax/APIs compatible with the
repository's declared development environment. Do not install, upgrade, relink,
or reconfigure the maintainer's toolchain merely to execute a patch helper.

### Interactive validation handoff

Validation proceeds from cheapest/highest-signal evidence to the most expensive
required gate:

1. inspect the changed-path set;
2. run `git diff --check`;
3. inspect the exact diff;
4. run applicable static/style/compile checks;
5. run the complete focal/affected regression set; and
6. run the integrated full suite only when the adaptive policy requires it.

Agents MUST NOT repeatedly request the integrated full suite after every small
intermediate edit. A failed earlier gate stops progression to a more expensive
gate. Intermediate focal evidence accumulates, but it does not discharge a final
full-suite obligation when the owning top-level closure requires one.

The agent SHOULD provide the next validation commands directly in chat and
interpret the maintainer's returned output before advancing to the next
publication-sensitive step.


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
branch or Pull Request.

Ordinary maintainer-directed interactive work does not require a temporary local
branch or worktree solely for patch construction. Work in the maintainer's
current checkout, preserving unrelated work as required above.

For direct publication to `main`, synchronize with current `origin/main`, resolve
only genuine overlaps/precondition changes, run the validation required by the
final candidate, and publish only by a normal non-force update. Never leave
temporary remote branches behind.


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

Before actionable implementation/project work, apply **Mandatory
pre-implementation audit** above: inspect the current Issue when one exists,
current `origin/main`, owning durable records, blockers, and applicable
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
operations. Helpers handed to the maintainer for repository-content changes MUST
NOT require GitHub API credentials and MUST NOT create, close, relabel, assign,
or move Issues/Project items. The `protos-project-docs` direct-publication
exception may use authenticated repository APIs only within its bounded
repository-publication rules; live Issue/Project coordination remains a separate
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

Repository publication follows **Interactive local contribution and publication
workflow** above. Live GitHub coordination does not reserve mutable repository
files or semantics; agents MUST still synchronize with current `origin/main`,
inspect overlaps, and preserve concurrent work.

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
Sub-issues require independently meaningful work; temporary patch versions,
diagnostics, launcher attempts, and cost-driven micro-slices do not become
project work merely because they helped execution.

<!-- BUG-FAMILY-POLICY -->
### Defect and regression work

Confirmed or credibly reproducible defects/regressions in behavior that Protos
already intends to provide use the `BUGxxx` family when the defect has durable,
independently trackable project identity. `BUGxxx` names the defect, not a
particular attempted fix.

Appropriate `BUGxxx` scope includes implementation regressions against already
defined semantics, build/test/package/CI failures, broken maintained tooling,
and other failures where the expected project behavior is already sufficiently
determined to diagnose and repair without inventing a new contract.

Keep these boundaries explicit:

- `BUGxxx` MUST NOT define new observable language semantics, public API policy,
  or durable platform/runtime architecture merely to make a failure disappear;
- when diagnosis exposes an unresolved substantive semantic choice, block only
  the affected bug work and route the choice through the applicable `Dxxx`
  decision and explicit approval gate;
- when diagnosis exposes a durable host/runtime architecture choice, route it
  through `PLATxxx` rather than embedding it in the bug fix;
- a performance regression whose primary problem is performance engineering may
  remain `PERFxxx`; a correctness defect found during performance work may use a
  `BUGxxx` item when it has independent defect identity;
- feature work, planned capability expansion, ordinary implementation slices,
  refactoring without a defect, and speculative cleanup do not become
  `BUGxxx` merely because they change code;
- one bug may require multiple diagnostic or patch attempts without allocating a
  new top-level `BUGxxx`; create a separate bug only for an independently
  meaningful defect; and
- if investigation proves the reported behavior is only local checkout
  divergence, unsupported environment state, duplicate reporting, or otherwise
  not a repository defect, close the Issue with that evidence rather than
  manufacturing a code change.

New `BUGxxx` identifiers follow the normal collision-safe allocation policy,
carry `family:BUG`, use GitHub Issues/Project for live coordination, and obey the
same audit, validation, publication, changelog, versioning, and design-authority
rules as the affected project surface.

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
`guillermomolina/protos-project-docs:docs/project/work/DOC001/DOC001_PROGRAMMING_DOCUMENTATION.md`.

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

`guillermomolina/protos-project-docs:docs/project/registries/IMPLEMENTATION_BLOCKERS.md`
is the durable project-record ledger for implementation work that cannot proceed
without unresolved normative semantics. It records implementation state only;
it is not part of the Protos specification.

When implementation work reaches a semantic dependency that is not normatively
closed or otherwise precise enough to implement without guessing:

- do not invent the missing semantics;
- do not block unrelated implementation work;
- add or update a blocker in
  `guillermomolina/protos-project-docs:docs/project/registries/IMPLEMENTATION_BLOCKERS.md`;
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
`guillermomolina/protos-project-docs:docs/project/registries/IMPLEMENTATION_BLOCKERS.md`.
Re-check every relevant `BLOCKED` or `READY` entry against the current normative
specification on the current `main` branch of `guillermomolina/protos`.
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
- `spec/semantics/MATCHING.md` — primary normative owner of ratified matching-protocol, matcher-outcome, and structural-projection semantics.

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
`guillermomolina/protos-project-docs:docs/project/architecture/CORE_BOOTSTRAP_ARCHITECTURE.md`.
Source for distributable Core behavior that can be expressed in Protos belongs
under `protos/lib/core/`.
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

Operator precedence, associativity, the fixed standard operator surface, contextual "...", trailing closures, indexed access, and assignment must follow the grammar rather than host-language conventions.

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

Validation scope comes from the definitive changed-file delta plus a conservative
dependency closure, not from a work-item identifier. Tests are evidence for
executable behavior, not a ritual for every repository edit.

Classify the final candidate by impact:

- **Executable/test impact** — production/distributable source, executable test
  infrastructure, build/generation/packaging/runtime configuration, or another
  change capable of altering executable behavior. Run the complete affected test
  set and the broader gate selected by the policy below.
- **Specification-only** — run applicable specification governance,
  consistency/changelog/static checks and any focused executable guard that
  consumes the changed artifact. Normative text alone does not require the full
  executable suite.
- **Documentation/governance-only** — run applicable formatting, integrity,
  link, governance, or other static checks plus any focused guard that directly
  consumes the changed artifact. Unrelated executable suites are not required.
- **Mixed** — use the strongest applicable class; adding documentation or
  changelog changes never weakens executable-impact requirements.

Do not classify by extension alone: build files, generators, packaging scripts,
or runtime configuration may have executable impact.

<!-- PERF005-B2 IMPACT-AWARE-TOOL-VALIDATION -->
### Impact-aware validation and integrated full-suite discipline

`scripts/validation_impact.py` is the deterministic fail-closed selector for
publication deltas, and `scripts/publication_validation.py` validates an
immutable candidate using that selection. Use their result when deterministic
commit-range evidence is useful; do not duplicate or second-guess their path
mapping in ad-hoc prose.

The governing invariants are:

- focused validation means the complete demonstrated affected set, not one
  convenient regression;
- shared production/library/runtime/compiler/parser/filesystem/module surfaces,
  cross-tool deltas, unknown/unmapped executable paths, build/generation changes,
  shared executable test infrastructure, or ambiguous dependency closure require
  `FULL`;
- only explicitly mapped bounded Tool-local deltas may use a reduced profile;
- reduced profiles are for intermediate work whose top-level executable owner
  remains open and whose semantics/architecture are already settled;
- closing/reconciling a top-level executable item requires one `FULL` validation
  over the final closure candidate after cheaper gates pass.

The canonical integrated full-suite command is:

    make test

`make test` covers both ordinary Java/JUnit and native Protos tests. Use
`make test-java` or `make test-protos` only when exactly one whole lane is the
required validation. Direct Maven invocation is appropriate for intentional
focal Java/JUnit sets and other bounded Maven-specific checks; unrestricted
`mvn test` is not the canonical integrated full-suite command.

Do not repeatedly run/request `make test` for intermediate edits. Follow
**Interactive validation handoff**: cheapest/highest-signal gates first, then pay
for the integrated suite only when impact/closure policy requires it and the
candidate plus moving finalization metadata are stable.

Expensive validation belongs to exact candidate bytes and relevant dependency
state. A pure credential/network/push retry does not invalidate already-passing
validation; changed candidate bytes or relevant dependencies require
reassessment.

Validation reporting SHOULD identify the selected impact/profile, affected test
set, applicable PASS/FAIL evidence, skipped/deferred broader validation, and the
top-level item that owns any deferred full-suite obligation.

<!-- PERF005-C RETIREMENT-CONDITION -->
The impact-routing policy is independent of its execution mechanism. A future
official Test Tool path may replace current host-side execution only if it
preserves deterministic affected-set selection, failure propagation, required
isolation, complete integrated validation, fail-closed unknown-state handling,
and auditable evidence.

### Test placement

Prefer Protos-level tests for observable Protos language semantics, conformance,
regressions, and user-visible standard-library behavior. A Java test alone SHOULD
NOT replace reasonably expressible Protos-level coverage merely because the
implementation is Java or JUnit is easier.

Use Java tests for Java-side contracts that are not meaningfully observable from
Protos: internal APIs/representations/invariants, parser/lowering machinery,
Truffle/JVM integration, host interop, bootstrap code, and harness
infrastructure. Do not contort a Protos test to reach a purely internal Java
contract. Keeping an additional focused Java regression is fine when it adds
implementation-level diagnostic value.

### Static verification

For source changes, run the narrowest applicable formatter/linter/compiler/static
check for the modified scope unless unavailable or disproportionately expensive.
Prefer check modes that do not rewrite files or trigger unrelated build phases.

Static verification MUST NOT implicitly run tests when tests are not part of the
selected gate. Formatting/check tooling MUST NOT introduce unrelated changes.
Report unavailable or skipped applicable checks rather than claiming a clean
result.

Do not add warning suppressions merely to make tooling pass. Use a narrowly
scoped suppression only when the warning is genuinely inapplicable and the
reason is defensible.

### Testing philosophy

Tests validate current Protos semantics, not implementation accidents. Prefer
small semantic regressions and workloads proportional to the correctness
property being proved.

Do not keep benchmark/stress/profiling/diagnostic-scale workloads in ordinary
tests when a smaller evidence-backed workload proves the same property; route
larger workloads to their purpose-specific surfaces.

Do not weaken semantic coverage, replace required integration evidence with
mocks, skip failures, or add production fast paths merely to reduce test cost.
Aggregate validation may legitimately be expensive because it composes many
checks; optimize/reclassify unnecessarily expensive constituents instead of
weakening the aggregate contract.

When fixing a semantic bug, add/update a focused regression when tests are part
of the work. Where useful, exercise the same semantic rule through multiple
execution paths that could diverge. Never weaken a test to make incorrect
implementation pass; if a test contradicts the current specification, report
that contradiction instead of treating the test as authoritative.

Operational boundaries

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
  English; and
- keep specification revisions separate: specification-only or
  documentation-only commits do not increment the implementation version merely
  because `spec/` or `docs/` changed, and normative specification changes remain
  recorded in `spec/PROTOS_SPEC_CHANGELOG.md`.

For concurrent interactive work, **derive and materialize moving implementation
metadata at finalization, not at patch start**. Implement and focal-validate the
substantive source/test delta first. Do not reserve the next Maven version or add
its root changelog section early merely to begin work.

Immediately before the final candidate is prepared, re-read the current
`pom.xml`, root `CHANGELOG.md`, and relevant `origin/main` state, derive the
then-current next implementation version, and add the matching version/changelog
metadata. If another agent consumed an earlier expected next version, recompute
the metadata from current state; do not discard or restart otherwise valid
substantive work unless the concurrent publication actually overlaps or changes
one of its semantic/implementation preconditions.

Late metadata materialization does not weaken atomicity. The required version
bump and matching changelog entry MUST still be present in the same final commit
as the implementation change, and the required final validation applies after
that metadata has been materialized.

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
`guillermomolina/protos-project-docs:docs/project/work/DIST001/DIST001_RELEASE_POLICY.md`.

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


## Repository test placement
<!-- REPOSITORY TEST-PLACEMENT -->

Tests SHOULD demonstrate observable Protos behavior at the highest useful level.

When behavior can be tested faithfully through ordinary Protos source and the
bundled Test Tool, prefer Protos-owned conformance or regression coverage.
Java/JUnit remains appropriate when the primary evidence is Java, Truffle,
compiler/backend, runtime, scheduler, native/host integration, or an independent
bootstrap boundary that cannot be proved by the Test Tool alone.

Do not keep duplicate Java and Protos tests merely to create two nominal owners
for the same semantic policy. Retained Java coverage is appropriate when it proves
a materially different host/runtime/bootstrap invariant.

This placement rule does not require a global semantic-ownership registry,
ownership manifest, or publication guard. The owning change and its tests provide
the evidence for why coverage belongs in one lane or is intentionally split.

### Protos test assertion authoring
<!-- LIB016 NEW-PROTOS-TEST-ASSERTIONS -->

New Protos tests MUST NOT define local assertion helpers that duplicate the
semantics of the ratified `std:test/Assertions` operations. When a new test needs
assertion-style Boolean failure, use `Assertions.require(condition)`. When it
needs to assert that a body signals an Error matching a prototype, use
`Assertions.signals(errorPrototype, body)`.

This rule does not require every Protos test to import `std:test/Assertions`.
Tests whose expected outcome is naturally expressed by the program's final value
or by the corpus manifest/TestPlan expectation may continue to use those forms.
Do not wrap them in assertions merely for stylistic uniformity.

Existing Protos tests that predate this rule are not required to migrate
opportunistically during unrelated work. Any repository-wide reconciliation of
those tests must be handled as separately tracked work.

Legacy Java/JUnit tests that predate this rule are not required to migrate
opportunistically during unrelated work. Their bounded repository-wide
reconciliation is owned separately by TEST002.\n\n### TOOL009 M2 test ownership for newly added Protos tests
<!-- TOOL009 M2 NEW-TEST-OWNERSHIP -->

During D152 M2, production-test ownership migration is an explicit domain
allowlist. Agents MUST NOT infer suite-native ownership merely because a test
uses `std:test/Test`, `std:test/Assertions`, lives near already migrated tests,
or could technically execute through the logical Case runner.

The current M2 domain states are:

- `protos/tests/conformance/library/test/` — **SUITE_NATIVE_ADMITTED**. New
  production tests in this domain MUST use the suite-native authoring model:
  import `std:test/Test`, expose a finite frozen `tests` Array of named Tests,
  keep test behavior inside Test bodies rather than executing it during module
  discovery, and use the temporary `suite-native` manifest marker while the M2
  bridge exists.
- `protos/tests/conformance/library/text/` — **SUITE_NATIVE_ADMITTED**. New
  production tests in this domain MUST use the suite-native authoring model:
  import `std:test/Test`, expose a finite frozen `tests` Array of named Tests,
  keep test behavior inside Test bodies rather than executing it during module
  discovery, and use the temporary `suite-native` manifest marker while the M2
  bridge exists.
- `protos/tests/conformance/library/collections/` — **SUITE_NATIVE_ADMITTED**.
  New production tests in this domain MUST use the suite-native authoring model:
  import `std:test/Test`, expose a finite frozen `tests` Array of named Tests,
  keep test behavior inside Test bodies rather than executing it during module
  discovery, and use the temporary `suite-native` manifest marker while the M2
  bridge exists.
- Core and central language-semantics conformance domains — including
  `core-surface/` and other tests whose primary subject is Core/runtime language
  semantics — are **NOT_ADMITTED_DURING_ACTIVE_AUD009_REFACTORING**. Preserve
  their current owner and manifest/TestPlan form. Do not opportunistically move
  them to suite-native ownership. TOOL009 migration of those domains requires
  explicit coordination with the project owner before the first ownership
  change.
- Every other production-test domain remains **CURRENT_OWNER** until explicitly
  admitted by a bounded TOOL009 M2 migration. New tests there MUST follow the
  domain's current authoritative owner rather than creating a mixed ownership
  transition implicitly.

When a domain is admitted to suite-native ownership, the same bounded migration
work MUST update this allowlist. Do not use a broad repository-wide rule such as
"all new Protos tests are suite-native" while M2 is incomplete.

JUnit-owned component fixtures under `protos/tests/tooling/` are not production
TestPlan ownership merely because they contain Protos source. A fixture that is
executed externally to prove discovery, scheduling, rematerialization, result
classification, bootstrap, host/runtime, or another independent component
invariant SHOULD remain externally owned unless separate work deliberately
changes that evidence boundary.

For a suite-native source, D153 discovery must remain observational with respect
to test execution: constructing/importing declarations is allowed, but test
behavior, mutable test state, expected failures, and other case effects belong
inside the selected Test body. The physical source file is not the logical Case
identity.

This section is temporary migration policy. M3/M4 must remove or replace it when
the incumbent production-test ownership path and `suite-native` migration marker
no longer exist.

## Guest execution backend and Java execution tests
<!-- AUD012 PLAT035 BYTECODE-ONLY-EXECUTION -->

PLAT035 establishes Truffle Bytecode DSL as the only executable Protos backend.
The canonical AST remains the backend-independent semantic representation; it is
not an executable fallback.

The retired executable-AST backend, including `CanonicalToTruffleLowerer`,
`ProtosExpressionNode`, `ProtosRootNode`, `ProtosRootFactory`,
`ProtosExecution`, and the associated legacy execution-node tree, MUST NOT be
reintroduced.

Production and production-representative guest execution must run inside the
appropriate initialized Polyglot/Process execution context and use the canonical
Bytecode-backed execution boundary. Source-backed closures crossing Context
ownership boundaries must be rematerialized for the entered
`ProtosLanguageContext`; a CallTarget owned by another Context must not be reused.

`ProtosSourceCompiler` itself is not globally forbidden: compiler/frontend,
backend, compile-only, architecture, or other explicitly bounded implementation
tests may legitimately exercise it. New direct Java guest-entry paths must not be
introduced merely for convenience. Ordinary Java semantic tests that need real
guest execution should use the established hosted/test execution boundary unless
the test is explicitly about a lower-level compiler or execution component.

`scripts/legacy_execution_guard.py` is the fail-closed repository prevention
gate for growth of direct/legacy execution dependencies and for reintroduction
of the retired executable-AST backend. Do not weaken, bypass, or reset that gate
to accommodate a new dependency; classify and justify the execution path or use
the canonical Bytecode-backed boundary.\n
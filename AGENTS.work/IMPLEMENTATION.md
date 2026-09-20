# Protos implementation work

These instructions apply to `Ixxx`, `CLIxxx`, `DISTxxx`, `LMxxx`, and
familyless implementation work, and to the shared implementation discipline
of every task that changes code or tests (`BUG`, `LIB`, `TOOL`, `PERF`, and
`TEST` work reads this file too).

Role: implement faithfully what the specification and the ratified decisions
define. A ratified `Dxxx`/`PLATxxx` record is authoritative for the work that
consumes it: read the record, then inspect the directly governing normative
owners (canonical map in the repository-root AGENTS.md) and the affected
implementation and tests. Do not repeat the design-space investigation.
Expand only when concrete current-repository evidence conflicts with the
record; then report the conflict explicitly.

Reasoning order: applicable instruction files → durable decision/work record
when one governs → governing specification sections → affected implementation
and tests → derive constraints → implement. Do not design an implementation
first and then search the specification for justification.

Before implementation work, review the relevant entries of the durable
blocker ledger (section below) against the current normative specification on
the current `main` branch of `guillermomolina/protos`.

Durable project records follow the DOC002 role-first path policy in
`AGENTS.work/REFERENCE.md`.

## Cost-aware task decomposition

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
hierarchy** rule in `AGENTS.work/COORDINATION.md`; a textual parent note alone is
not sufficient.

Before starting each subsequent slice, request a fetch and inspect the current
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

## Search, do not guess the relevant documents

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

## Specification and documentation disagreements

Apply the normative authority and document responsibilities defined in the
repository-root AGENTS.md and in the applicable `spec/AGENTS.md`.

If documentation, specification, tests, and implementation appear to disagree:

- identify the exact files and relevant clauses;
- determine which source owns the behavior;
- report any genuine contradiction;
- do not silently choose the implementation or invent semantics.

Implementation difficulty is not a normative gap. Declare a normative blocker
only when the applicable normative material leaves an observable semantic
choice genuinely unresolved.

## Patch-authoring environments

Lack of a local checkout, Maven, or other build tooling in the agent environment
does not waive the repository/specification audit when repository contents are
available through another read-capable mechanism.

Repository-content publication follows the publication and repository-content
safety rules in the repository-root AGENTS.md. Lack of local tooling MUST NOT be
used to invent a different publication path.

## Interactive validation handoff

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

## Compatibility

During language version "0.1", the design may evolve, but implementation changes must still follow the current specification.

Do not preserve accidental implementation behavior when it contradicts the specification.

Conversely, do not introduce a breaking semantic change merely to simplify existing code.

If existing implementation behavior and the current specification disagree, the specification wins unless the user explicitly decides otherwise.

Clearly report compatibility consequences when changing already implemented behavior.

## Test execution

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

## Static verification

For source changes, request the narrowest applicable formatter/linter/compiler/static
check for the modified scope unless unavailable or disproportionately expensive.
Prefer check modes that do not rewrite files or trigger unrelated build phases.

Static verification MUST NOT implicitly run tests when tests are not part of the
selected gate. Formatting/check tooling MUST NOT introduce unrelated changes.
Report unavailable or skipped applicable checks rather than claiming a clean
result.

Do not add warning suppressions merely to make tooling pass. Use a narrowly
scoped suppression only when the warning is genuinely inapplicable and the
reason is defensible.

## Dependencies

Avoid adding dependencies when the functionality is small and reasonably implementable within the project.

Before adding a dependency, check whether the JDK, GraalVM/Truffle platform, or an existing project dependency already provides the required functionality.

Do not upgrade Java, GraalVM, Truffle, build tooling, parser libraries, or other foundational dependencies as part of unrelated work.

Keep GraalVM and Truffle versions deliberately pinned and compatible.

Dependency changes that affect the language implementation architecture should be explained before they are made.

## Source file size

Keep hand-written source files focused and reasonably small.

When a source file reaches approximately 600 lines, assess whether it contains separable responsibilities.

Do not normally let a hand-written source file exceed 800 lines; split it into cohesive components before adding substantial new code.

These thresholds are guidelines rather than mechanical limits.

Split by semantic or architectural responsibility, not into arbitrary numbered fragments.

Existing oversized files do not have to be split for a small unrelated change.

Generated files, vendored code, lock files, fixtures, and primarily declarative data are exempt.

## Consistency and reuse

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

## Source-code documentation

Documentation for new or materially changed code is proportional: explain what
the code alone cannot.

New or materially changed code MUST document:

- public APIs and externally observable contracts where the code itself does
  not make the contract sufficiently clear;
- non-obvious invariants, constraints, edge cases, and semantic assumptions;
  and
- implementation decisions whose rationale would not be evident to a future
  maintainer.

Do not add comments or documentation that merely restate self-explanatory
code. Documentation must explain contracts, constraints, or rationale rather
than narrate implementation syntax.

## Implementation versioning and changelog

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

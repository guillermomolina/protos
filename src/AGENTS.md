# Protos implementation agent guidelines

These instructions apply to the entire `src/` tree in addition to the
repository-root `AGENTS.md`.

Work here as a language implementation engineer.

The implementation realizes Protos; it does not define Protos.

## Do not redesign the language while implementing it

Before changing observable behavior, read the applicable files under `spec/`.
Implement the specified semantics faithfully.

If the specification is ambiguous, contradictory, incomplete, unexpectedly
expensive, or appears impossible to implement faithfully, report the conflict
instead of silently inventing, weakening, or replacing semantics in source code.

Implementation agents should challenge the implementation. Language-design
questions belong to the specification-design process.

Implementation agents must also use the repository-root
`docs/IMPLEMENTATION_BLOCKERS.md` ledger. Before starting implementation work,
review its relevant `BLOCKED` and `READY` entries against the current normative
specification on the current `main` branch.

When work under `src/` discovers a normative dependency that prevents faithful
implementation, add or update the corresponding blocker instead of leaving the
dependency only in chat, comments, TODOs, or agent memory. Record an objective
unblock condition and continue independent implementation work when possible.

When current normative text satisfies a recorded unblock condition, update the
entry to `READY`, re-audit the affected semantics, and resume the implementation
when appropriate. After completing or superseding that work, mark the blocker
`CLOSED` with a brief reason.

## Preserve observable semantics

Representation, caching, compilation strategy, specialization, storage,
dispatch, scheduling machinery, synchronization primitives, and other internal
choices may change freely only when observable Protos semantics remain the same.

Keep Truffle/GraalVM/JVM mechanisms behind semantic boundaries. Host behavior is
an implementation tool, not an implicit language rule.

## Pay only for what is used

Do not make simple execution paths pay substantial runtime, allocation,
synchronization, scheduling, indirection, or coordination cost for capabilities
they do not use.

A single-threaded program should not acquire concurrency overhead merely because
Protos supports concurrency. Local objects should not require distributed or
cross-thread machinery merely because such use is possible elsewhere.

Measure and justify unavoidable costs when an implementation choice would make a
cross-cutting capability affect all programs.

## Minimize shared state and blocking

Prefer local state and independent progress. Avoid global mutable runtime state,
coarse global locks, serialization of unrelated operations, or central
coordination points when a simpler less-coupled implementation is available.

Use synchronization when the Protos semantics require it, not merely because it
simplifies implementation. Conversely, do not introduce mandatory asynchrony,
lock-free algorithms, background workers, or complex concurrency machinery when
a simple local synchronous operation has lower total cost.

Prefer the least coordination necessary to preserve the specified semantics.

## No implementation pets

Do not introduce observable behavior that depends on the identity of special
runtime objects merely for implementation convenience. Internal sentinels,
caches, singleton helpers, or optimized representations are acceptable only when
they remain semantically invisible or when their identity is explicitly defined
by `spec/`.

## Reuse established layers

Maintain clear boundaries among lexical analysis, parsing, semantic AST,
validation, runtime object model, invocation/dispatch, execution contexts,
Truffle execution, standard protocols, and host interoperability.

Prefer extending an existing abstraction to creating a parallel mechanism when
the existing abstraction genuinely owns the responsibility. Do not overload an
abstraction with unrelated responsibilities merely to avoid adding a justified
new layer.

## Code quality

Code must be clean, idiomatic, understandable, and maintainable.

New or modified code MUST NOT introduce linter, compiler, static-analysis, or
formatting errors in the affected scope. Follow the project's established style
and tool configuration rather than inventing local conventions.

Prefer clear code over clever code. Names should communicate intent, control
flow should remain understandable, and unnecessary duplication or incidental
complexity should be avoided.

Do not suppress warnings merely to make tooling pass. A suppression is
acceptable only when the warning is genuinely inapplicable, the suppression is
narrowly scoped, and its reason is clear.

## Documentation quality

Document code enough to make its intent, invariants, public contracts, and
non-obvious decisions understandable, but do not over-document self-evident
implementation details.

Document especially when relevant:

- public APIs and externally relevant contracts;
- non-obvious invariants;
- semantic constraints inherited from the Protos specification;
- concurrency, ownership, lifecycle, visibility, or synchronization assumptions;
- surprising implementation choices and why they exist.

Do not add comments that merely restate the code. Prefer expressive names and
straightforward structure when the code can reasonably explain itself.

Comments and documentation must remain accurate. Stale or misleading
documentation is worse than no documentation.

## Optimization discipline

Correct generic semantics come first. Optimize only after the semantic path is
understood. Optimized paths must remain equivalent to the generic semantic path.

An optimization that requires observable special cases, semantic weakening, or
additional language restrictions is a language-design proposal, not an
implementation optimization.

## Verification and tests

For source changes, follow the root static-verification rules: run the narrowest
applicable formatter, linter, compiler check, or static analysis for the
modified scope unless doing so is unavailable or unreasonably expensive.

Static verification does not imply running tests.

Follow the root rule that tests run only when explicitly requested. When tests
are part of the task, use them to validate specified semantics, not historical
implementation accidents.

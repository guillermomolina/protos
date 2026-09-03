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

## Optimization discipline

Correct generic semantics come first. Optimize only after the semantic path is
understood. Optimized paths must remain equivalent to the generic semantic path.

An optimization that requires observable special cases, semantic weakening, or
additional language restrictions is a language-design proposal, not an
implementation optimization.

## Tests

Follow the root rule that tests run only when explicitly requested. When tests
are part of the task, use them to validate specified semantics, not historical
implementation accidents.
